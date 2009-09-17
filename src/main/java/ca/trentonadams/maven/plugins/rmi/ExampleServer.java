package ca.trentonadams.maven.plugins.rmi;

import org.apache.commons.configuration.PropertiesConfiguration;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by IntelliJ IDEA.
 * <p/>
 * Created :  Jun 26, 2008 1:32:02 PM MST
 * <p/>
 * Modified : $Date$
 * <p/>
 * Revision : $Revision$
 *
 * @author trenta
 */
public class ExampleServer extends UnicastRemoteObject implements
    RIExampleServer
{
    private static PropertiesConfiguration configuration;

    public ExampleServer(int port) throws RemoteException
    {
        super(port);
    }

    public String getHello() throws RemoteException
    {
        System.out.println("called getHello()");
        try
        {
            Naming.unbind("//localhost:1199/testrmi");
        }
        catch (NotBoundException e)
        {
            e.printStackTrace();
        }
        catch (MalformedURLException e)
        {
            e.printStackTrace();
        }
        System.gc();
        return "Hello World!!!";
    }

    public static void main(String args[])
    {
        RIExampleServer rmiServer = null;
        try
        {
            int portStart;
            int portEnd;
            portStart = 1080;
            portEnd = 1090;

            for (int port = portStart; port <= portEnd; port++)
            {
                try
                {
                    System.out.println("creating: " + port);
                    rmiServer = new ExampleServer(port);
                    break;
                }
                catch (Throwable throwable)
                {
                    System.err.println(throwable);
                }
            }
            System.out.println("binding: //localhost:1199/testrmi");
            Naming.rebind("//localhost:1199/testrmi", rmiServer);
            rmiServer = null;
        }
        catch (MalformedURLException e)
        {
            rmiServer = null;
            System.err.println(e);
        }
        catch (RemoteException e)
        {
            rmiServer = null;
            System.err.println(e);
        }

        System.gc();
    }
}
