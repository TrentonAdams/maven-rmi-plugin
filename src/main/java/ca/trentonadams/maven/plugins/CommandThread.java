package ca.trentonadams.maven.plugins;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by IntelliJ IDEA.
 * <p/>
 * Created :  Jun 25, 2008 11:59:07 PM MST
 * <p/>
 * Modified : $Date$
 * <p/>
 * Revision : $Revision$
 *
 * @author trenta
 */
public final class CommandThread extends Thread
{
    private String commandString;
    private Process lsProc;
    private BufferedReader stdout;
    private BufferedReader stderr;

    private boolean failed;
    private boolean forceExit;
    private InputStream stdoutIS;
    private InputStream stderrIS;
    private RunRMI runRMI;

    /**
     * The primary constructor for creating a new CommandThread.
     *
     * @param runRMI        the RunRMI plugin class, for using the logging
     *                      facility
     * @param commandString the command to run, along with it's arguments
     */
    public CommandThread(RunRMI runRMI, String commandString)
    {
        super(commandString); // thread name
        this.runRMI = runRMI;
        this.commandString = commandString;
    }

    /**
     * Starts the process, and waits for it to exit, or forces it to exit if
     * {@link CommandThread#shutdown()} is called.
     */
    public void run()
    {   // BEGIN run()
        try
        {
            startProcess();
            waitForProcess();
        }
        catch (InterruptedException threadInterruptedException)
        {   // nothing wrong with interrupting this thread.
            runRMI.getLog().error("The process was interrupted while " +
                "waiting for it to exit", threadInterruptedException);
            dumpProcOutput();
        }
        catch (MojoExecutionException e)
        {
            failed = true;
            runRMI.getLog().error(e);
        }
        finally
        {
            try
            {
                if (stdoutIS != null)
                {
                    stdoutIS.close();
                }
                if (stderrIS != null)
                {
                    stderrIS.close();
                }
                if (stderr != null)
                {
                    stderr.close();
                }
                if (stdout != null)
                {
                    stdout.close();
                }
            }
            catch (IOException e)
            {
                runRMI.getLog().error(e);
            }
            finally
            {
                if (lsProc != null)
                {
                    lsProc.destroy();
                    try
                    {
                        lsProc.waitFor();
                    }
                    catch (InterruptedException e)
                    {
                        runRMI.getLog().error(e);
                    }
                    runRMI.getLog().info(getName() +
                        " has exited with value: " + lsProc.exitValue());
                }
            }
        }
    }   // END run()

    /**
     * Waits for the process started by startProcess()
     *
     * @throws InterruptedException if the process was interrupted.
     */
    private void waitForProcess() throws InterruptedException
    {   // BEGIN waitForProcess()
        int exitValue = -255;
        while (!forceExit && exitValue == -255)
        {
            sleep(100);
            try
            {
                exitValue = lsProc.exitValue();
                // process exited
                if (exitValue != 0)
                {
                    failed = true;
                    runRMI.getLog().info("commandstring: " + commandString);
                }
            }
            catch (IllegalThreadStateException ignored)
            {
                // boy does it suck to catch an exception for flow control
            }
            dumpProcOutput();
        }

        if (forceExit)
        {
            lsProc.destroy();
        }
    }   // END waitForProcess()

    /**
     * Attempt to dump the current waiting process output of whatever is
     * available.
     */
    public void dumpProcOutput()
    {
        String buff = null;
        try
        {
            while (!forceExit && stdout.ready() &&
                (buff = stdout.readLine()) != null)
            {
                runRMI.getLog().info("STDOUT : " + buff);
            }

            while (!forceExit && stderr.ready() &&
                (buff = stderr.readLine()) != null)
            {
                runRMI.getLog().error("STDERR : " + buff);
            }
        }
        catch (IOException e)
        {
            runRMI.getLog().error(e);
        }
    }

    /**
     * Create the process for the command, and setup it's stdout/stderr.
     *
     * @throws MojoExecutionException if running a process fails
     */
    private void startProcess() throws MojoExecutionException
    {
        Runtime runtime;
        String[] shellCommand;

        runtime = Runtime.getRuntime();
        try
        {
            shellCommand = commandString.split(" ");
            setName(shellCommand[0]);

            lsProc = runtime.exec(shellCommand, new String[]{});
            runRMI.getLog().debug(commandString);

            stdoutIS = lsProc.getInputStream();
            stderrIS = lsProc.getErrorStream();
            stdout = new BufferedReader(new InputStreamReader(stdoutIS));
            stderr = new BufferedReader(new InputStreamReader(stderrIS));
        }
        catch (IOException ioException)
        {
            throw new MojoExecutionException(getName() + " run failed",
                ioException);
        }
    }

    /**
     * @return the command string that was passed to the constructor.
     */
    public String getCommandString()
    {
        return commandString;
    }

    public void shutdown()
    {
        forceExit = true;
    }

    public boolean hasFailed()
    {
        return failed;
    }
}   // END CommandThread
