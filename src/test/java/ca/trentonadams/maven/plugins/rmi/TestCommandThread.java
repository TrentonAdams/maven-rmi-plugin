package ca.trentonadams.maven.plugins.rmi;

import junit.framework.TestCase;
import ca.trentonadams.maven.plugins.*;

/**
 * Created by IntelliJ IDEA.
 * <p/>
 * Created :  Apr 4, 2009 10:33:27 AM MST
 * <p/>
 * Modified : $Date$ UTC
 * <p/>
 * Revision : $Revision$
 *
 * @author trenta
 */
public class TestCommandThread extends TestCase
{
    public void testRunProc()
    {
        CommandThread commandThread;
        commandThread = new CommandThread(new RunRMI(), "/usr/bin/java -server -Djava.rmi.server.ignoreStubClasses=true -Djava.rmi.server.codebase=file:/home/trenta/Documents/Development/cv_accounting/rmi/target/cv_accounting.jar -cp /home/trenta/Documents/Development/cv_accounting/rmi/target/classes:/home/trenta/.m2/repository/commons-configuration/commons-configuration/1.5/commons-configuration-1.5.jar:/home/trenta/.m2/repository/commons-collections/commons-collections/3.1/commons-collections-3.1.jar:/home/trenta/.m2/repository/commons-lang/commons-lang/2.3/commons-lang-2.3.jar:/home/trenta/.m2/repository/commons-logging/commons-logging/1.1/commons-logging-1.1.jar:/home/trenta/.m2/repository/log4j/log4j/1.2.12/log4j-1.2.12.jar:/home/trenta/.m2/repository/logkit/logkit/1.0.1/logkit-1.0.1.jar:/home/trenta/.m2/repository/avalon-framework/avalon-framework/4.1.3/avalon-framework-4.1.3.jar:/home/trenta/.m2/repository/javax/servlet/servlet-api/2.3/servlet-api-2.3.jar:/home/trenta/.m2/repository/commons-digester/commons-digester/1.8/commons-digester-1.8.jar:/home/trenta/.m2/repository/commons-beanutils/commons-beanutils/1.7.0/commons-beanutils-1.7.0.jar:/home/trenta/.m2/repository/commons-beanutils/commons-beanutils-core/1.7.0/commons-beanutils-core-1.7.0.jar:/home/trenta/.m2/repository/commons-dbcp/commons-dbcp/1.2.1/commons-dbcp-1.2.1.jar:/home/trenta/.m2/repository/commons-pool/commons-pool/1.2/commons-pool-1.2.jar:/home/trenta/.m2/repository/xml-apis/xml-apis/1.0.b2/xml-apis-1.0.b2.jar:/home/trenta/.m2/repository/xerces/xercesImpl/2.0.2/xercesImpl-2.0.2.jar:/home/trenta/.m2/repository/org/postgresql/postgresql/8.2-506/postgresql-8.2-506.jar:/home/trenta/.m2/repository/com/ibatis/ibatis2-sqlmap/2.1.7.597/ibatis2-sqlmap-2.1.7.597.jar:/home/trenta/.m2/repository/com/ibatis/ibatis2-common/2.1.7.597/ibatis2-common-2.1.7.597.jar:/home/trenta/Documents/Development/cv_accounting/rmi/src/main/config:/home/trenta/Documents/Development/cv_accounting/rmi/target/cv_accounting.jar ca.climbingvine.cvacct.rmi.AccountingServer");
        commandThread.start();
    }
}
