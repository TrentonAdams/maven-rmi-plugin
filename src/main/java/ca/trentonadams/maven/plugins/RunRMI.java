package ca.trentonadams.maven.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.DuplicateRealmException;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import static java.lang.Thread.sleep;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

/**
 * Goal which runs an rmiregistry (if not already started) and a spawned java
 * process to run the rmi service.
 * <p/>
 * When not using the forking option, press Ctrl-C to end the maven and rmi
 * processes.
 * <p/>
 * TODO : Use maven's run command tool.  I can't remember what dependency has
 * it, but at some point I want to at least try it.
 * <p/>
 * TODO : Verify that the forking mechanism is being used properly.  i.e. What
 * happens to the thread?  Does the thread get killed, leaving the running
 * process to increase it's STDOUT/STDERR buffer size indefinitely, thereby
 * having a memory leak?
 * <p/>
 * TODO : make rmi registry port configurable
 * <p/>
 * TODO : make java.policy configurable.  Is this really needed though, or will
 * it work from classpath?
 *
 * @goal rmi
 * @requiresDirectInvocation false
 * @requiresProject true
 * @description runs the rmiregistry service (if not already started) and a
 * spawned java process to run the rmi service.
 * @inheritByDefault false
 * @requiresDependencyResolution compile
 */
@SuppressWarnings({"UnusedDeclaration"})
public class RunRMI extends AbstractMojo
{
    /**
     * THIS IS AN EXPERIMENTAL OPTION.  We need to verify several things before
     * approving this for normal use.  See the TO-DO items in the javadoc of the
     * plugin.
     * <p/>
     * Fork the commands, and exit, leaving them running in the background. Take
     * special note that if you use this option, the plugin will no longer be
     * responsible for managing the processes.  If you need to kill them, you
     * will need to do it yourself through some other mechanism.  That may be a
     * remote call to a method that asks the rmi object to end, killing it from
     * the command line, a process management tool, or some other mechanism.
     * <p/>
     * TODO: If forking, require parameters that tell the system to connect
     * stdout/stderr to files.
     *
     * @parameter expression="false"
     */
    private boolean fork;

    /**
     * Run the given commands
     */
    private List<String> commands = new Vector<String>();

    /**
     * Pass in the extra classpath items.
     *
     * @parameter expression="${compile.classpath}"
     */
    private String extraClasspath;

    /**
     * The rmi server class name.
     *
     * @parameter
     * @required
     */
    private String rmiServerClass;

    /**
     * @parameter expression="1099"
     * @required
     */
    private int registryPort;

    /**
     * Verification method name of remote method to call, expected return
     * string, and bind name
     *
     * @parameter
     */
    private String[] verify;

    /**
     * The Maven project object
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;


    private Registry registry;

    private String classpath;

    private List<CommandThread> threads;

    /**
     * The current build session instance.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * rmi codebase
     *
     * @parameter
     * @required
     */
    private String codebase;

    private Log mojoLog;

    public void execute() throws MojoExecutionException
    {
        initialize();
        startRegistry();
        // TODO location of java command needs to be configurable, or detectable
        commands.add(
            "/usr/bin/java -server -Djava.rmi.server.ignoreStubClasses=true " +
                "-Djava.rmi.server.codebase=file:" + codebase + " " +
/*                "-Djava.security.policy=file:java.policy " +*/
                "-cp command.classpath" +
                rmiServerClass);
        runCommands();
    }

    /**
     * Initialization tasks such as checking parameters are correct, creating
     * the classpath, etc.
     *
     * @throws MojoExecutionException if an unrecoverable error occurs.
     */
    private void initialize() throws MojoExecutionException
    {
        if (verify != null && verify.length != 3)
        {
            throw new MojoExecutionException(
                "The \"verify\" parameter is setup " +
                    "incorrectly.  Please specify a Remote interface class name, " +
                    "followed by a method name, followed by an expected string " +
                    "return value.");
        }
        else if (verify != null && verify.length == 3)
        {
            mojoLog = getLog();
            mojoLog.info("verification method: " + verify[0]);
            mojoLog.info("verification return value: " + verify[1]);
            mojoLog.info("verification bind name: " + verify[2]);
        }

        mojoLog.info("registry port: " + registryPort);

        threads = new Vector<CommandThread>();

        try
        {
            classpath = StringUtils.join(
                project.getCompileClasspathElements().iterator(),
                File.pathSeparator);
        }
        catch (DependencyResolutionRequiredException e)
        {
            throw new MojoExecutionException("Error getting classpath: ", e);
        }

        mojoLog.info("codebase: " + codebase);

        try
        {
            // TODO the class is never found, because something is wrong with the classpath.
            final ClassWorld world = new ClassWorld();

            //use the existing ContextClassLoader in a realm of the classloading space
            final ClassRealm realm = world.newRealm("plugin.maven.rmi",
                Thread.currentThread().getContextClassLoader());

            //create another realm for just the jars we have downloaded on-the-fly and make
            //sure it is in a child-parent relationship with the current ContextClassLoader
            final ClassRealm rmiRealm = realm.createChildRealm("rmi");

            final List elements = project.getCompileClasspathElements();

            for (final Object element : elements)
            {
                mojoLog.debug("element: " + element);
                rmiRealm.addConstituent(new File((String) element).toURL());
            }

            //make the child realm the ContextClassLoader
            Thread.currentThread()
                .setContextClassLoader(rmiRealm.getClassLoader());
        }
        catch (DuplicateRealmException e)
        {
            throw new MojoExecutionException("class loading modification " +
                "failed", e);
        }
        catch (DependencyResolutionRequiredException e)
        {
            throw new MojoExecutionException("class loading modification " +
                "failed", e);
        }
        catch (MalformedURLException e)
        {
            throw new MojoExecutionException("class loading modification " +
                "failed", e);
        }
    }

    /**
     * Starts the rmi registry by either forking the rmiregistry command, or
     * using the java internal LocateRegistry.createRegistry(port) API,
     * depending on if someone set the "fork" plugin configuration to true or
     * not.
     *
     * @throws MojoExecutionException if an unrecoverable error occurs.
     */
    private void startRegistry() throws MojoExecutionException
    {   // BEGIN startRegistry()
        try
        {
            registry = LocateRegistry.getRegistry(registryPort);
            registry.list();
            mojoLog.info("found registry");
        }
        catch (RemoteException e)
        {
            mojoLog.warn("rmiregistry does not exist, attempting " +
                "to create it...");
            mojoLog.debug("exception", e);
            registry = null;
        }

        if (registry == null)
        {
            if (fork)
            {   // BEGIN fork rmiregistry command
                mojoLog.info("forking registry command");
//                    commands.add("/usr/bin/rmiregistry");
                // TODO location of rmiregistry command needs to be
                // configurable or detectable
                runCommand("/usr/bin/rmiregistry " + registryPort);
                mojoLog.info("waiting for registry startup, " +
                    "press Ctrl-C if this takes too long...");
                boolean registryStarted = false;
                do
                {
                    try
                    {
                        Thread.sleep(100);
                        registry = LocateRegistry.getRegistry(registryPort);
                        registry.list();
                        registryStarted = true;
                    }
                    catch (RemoteException e)
                    {
                        mojoLog.debug("still waiting for registry " +
                            "startup, no need to be alarmed: ", e);
                    }
                    catch (InterruptedException e)
                    {
                        throw new MojoExecutionException("Interrupted " +
                            "while waiting for registry startup, " +
                            "exiting...", e);
                    }
                } while (!registryStarted);
            }   // END fork rmiregistry command
            else
            {   // BEGIN internal rmi registry
                mojoLog.info("starting internal registry...");
                try
                {
                    registry = LocateRegistry.createRegistry(registryPort);
                }
                catch (RemoteException e)
                {
                    mojoLog.debug("error creating non-forking internal " +
                        "registry", e);
                }
            }   // END internal rmi registry
        }
        else
        {
            mojoLog.info("registry: " + registry);
            mojoLog.info("using existing rmiregistry instance");
        }
    }   // END startRegistry()

    /**
     * Verify that the RMI server started up successfully.  This will only work
     * if the verify option has been configured in the POM.
     *
     * @throws MojoExecutionException if an unrecoverable error occurs.
     */
    private void verifyRMIServer() throws MojoExecutionException
    {   // BEGIN verifyRMIServer()
        try
        {
            if (verify != null && verify.length == 3)
            {
                mojoLog.info("waiting 1000 ms for rmi object to start");

                final Remote rmiServer = registry.lookup(verify[2]);
                final Method method = rmiServer.getClass().getMethod(verify[0]);
                final String returnValue = (String) method.invoke(rmiServer);
                mojoLog.debug("calling rmiServer.getHello(): " + returnValue);
                if (returnValue.equals(verify[1]))
                {
                    mojoLog.info("rmi startup verification successful");
                }
            }
            else
            {
                mojoLog.warn("\"verify\" configuration not setup, unable to " +
                    "verify successful rmi startup");
            }
        }
        catch (NotBoundException e)
        {
            try
            {
                mojoLog.info("bindings: " + Arrays.toString(registry.list()));
            }
            catch (RemoteException e1)
            {
                mojoLog.error(e1);
            }
            throw new MojoExecutionException(verify[2] + " not bound to the " +
                "registry", e);
        }
        catch (AccessException e)
        {
            throw new MojoExecutionException("access violation: ", e);
        }
        catch (RemoteException e)
        {
            throw new MojoExecutionException("remote exception: ", e);
        }
        catch (NoSuchMethodException e)
        {
            throw new MojoExecutionException("method does not exist, your " +
                "\"verify\" plugin configuration parameters are incorrect", e);
        }
        catch (IllegalAccessException e)
        {
            throw new MojoExecutionException("illegal access: ", e);
        }
        catch (InvocationTargetException e)
        {
            throw new MojoExecutionException("invocation target exception: ",
                e);
        }
    }   // END verifyRMIServer()

    /**
     * First starts the rmiRegistry.  Then runs through all the commands in the
     * commands instance variable.
     *
     * @throws MojoExecutionException if an unrecoverable problem occurs.
     */
    private void runCommands()
        throws MojoExecutionException
    {

        CommandThread newThread;

//        newThread = runCommand("/usr/bin/rmiregistry");
//        threads.add(newThread);

        for (String command : commands)
        {
            command = command.replaceAll("command.classpath", classpath
                + File.pathSeparator +
                extraClasspath + " ");
            mojoLog.info("RUNNING COMMAND: " + command);
            runCommand(command);
            mojoLog.info("sleeping 1000ms");
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                mojoLog.error(e);
            }
        }

        int deadThreads = 0;
        final int totalThreads = threads.size();
        boolean threadRemoved = false;

        if (fork)
        {
            mojoLog.info("forked process(es), exiting");
        }

        // Check to make sure the RMI server was bound, and is returning the
        // correct info
        try
        {
            verifyRMIServer();
        }
        catch (MojoExecutionException e)
        {
            mojoLog.error("verifyRMIServer() failed, exiting", e);
            killThreads(threads);
            throw e;
        }

        while (!fork && deadThreads < totalThreads)
        {   // keep running until all threads are dead.
            for (int index = 0; index < threads.size();
                 index = threadRemoved ? index : index + 1)
            {   // if thread removed, keep index value
                mojoLog.debug("dead:total = " + deadThreads + ":" +
                    totalThreads);
                threadRemoved = false;
                final CommandThread thread;
                thread = threads.get(index);
                try
                {
                    sleep(500);
                }
                catch (InterruptedException e)
                {
                    break;
                }

                if (!thread.isAlive())
                {   // if it is not alive, kill the rest.
                    mojoLog.debug("thread died : " + thread.getName());
                    deadThreads++;
                    threads.remove(index);
                    threadRemoved = true;
                    if (thread.hasFailed())
                    {
                        mojoLog.warn("One process has failed, let us kill " +
                            "them all.  All for one and one for all. :P");
                        killThreads(threads);
                        fork = true;
                        break;
                    }
                }
            }
        }   // END waiting for threads to die
    }

    /**
     * Kill all spawned process threads.
     *
     * @param threads the threads that were spawned using Runtime.exec().
     */
    private void killThreads(final List<CommandThread> threads)
    {
        for (final CommandThread thread : threads)
        {
            thread.dumpProcOutput();
            thread.shutdown();
            mojoLog.info("waiting for thread: " + thread.getName());
            for (int index = 0; index < 10; index++)
            {
                try
                {
                    sleep(100);
                    if (!thread.isAlive())
                    {
                        index = 10;
                    }
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Run a specific command in a thread.
     * <p/>
     * TODO : Make it wait until the thread reports that the process is off and
     * running, before returning.
     * <p/>
     * TODO : Come up with a more robost mechanism to provide series based
     * commands, where all others have to wait until this process exits. Perhaps
     * with a maximum timeout.
     *
     * @param commandString the command string including arguments separted by
     *                      spaces
     *
     * @throws MojoExecutionException if an unrecoverable error occurs;.
     */
    protected void runCommand(final String commandString)
        throws MojoExecutionException
    {
        final CommandThread newThread;

        newThread = new CommandThread(this, commandString);
        newThread.start();
        threads.add(newThread);
    }

}
