package ca.trentonadams.maven.plugins.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by IntelliJ IDEA.
 * <p/>
 * Created :  Jun 26, 2008 1:47:24 PM MST
 * <p/>
 * Modified : $Date$
 * <p/>
 * Revision : $Revision$
 *
 * @author trenta
 */
public interface RIExampleServer extends Remote
{
    public String getHello() throws RemoteException;
}
