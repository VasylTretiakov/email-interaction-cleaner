package eu.comways.altitude.cleaner;

import altitude.integrationServer.events.IncomingSessionAdapter;
import altitude.integrationServer.events.IncomingSessionEvent;
import altitude.integrationServer.exceptions.ISException;
import altitude.integrationServer.javaApi.*;
import altitude.integrationServer.types.CampaignQueuedEmails;
import altitude.integrationServer.types.QueuedEmail;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Scanner;

public class Main {

    private static int counter;
    private static Work w;
    private static boolean work = true;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        try {

            System.out.println("Please enter instance address:");
            final String instanceAddress = sc.next();
            System.out.println("Please enter user name:");
            final String userName = sc.next();
            System.out.println("Please enter password:");
            final String userPass = sc.next();
            System.out.println("Please enter campaign name:");
            final String campaignName = sc.next();
            System.out.println("Please enter FROM address:");
            final String fromAddress = sc.next();
            System.out.println("Please enter the number of interactions to clean:");
            counter = sc.nextInt();

            // Configure the javaApi with the parameters defined in the configuration file
            Configuration config = new XMLConfiguration(new File("config.xml"));
            Configurator.setConfiguration(config);

            // Initialize the IntegrationServer object
            IntegrationServer integrationServer = new IntegrationServer();

            // initialize the JavaApi objects
            //clearJavaApiReferences();
            // Request the IntegrationServer to login the requested agent
            Instance instance = integrationServer.login(instanceAddress, userName, userPass);
            System.out.println("Logged in");
            // Get the logged agent
            Agent agent = instance.getLoggedAgent();
            agent.setSite(agent.getDefaultSite());

            agent.addIncomingSessionListener(new IncomingSessionAdapter() {
                public void onIncomingSession(IncomingSessionEvent evt) {
                    agentOnIncomingSession(evt);
                }
            });
            System.out.println("Added session listener");
            w = agent.getAssignedCampaign(campaignName);
            w.open();
            System.out.println("Opened campaign");
            //w.signOn(true);
            //System.out.println("Signed on to campaign");
            //w.setReady(true);
            //System.out.println("Set ready in campaign");
            System.out.println("Waiting for sessions...");
            int c = 0;
            while (work) {
                try {
                    c++;
                    System.out.println(c + "...");
                    CampaignQueuedEmails[] cEmails = w.getCampaign().getQueuedEmails();
                    System.out.println("Got CampaignQueuedEmails of length " + cEmails.length);
                    QueuedEmail[] emails = cEmails[0].getQueuedEmails();
                    System.out.println("Got QueuedEmails of length " + emails.length);
                    int emailID = -1;
                    for (QueuedEmail email : emails)
                    {
                        if(email.getFrom().toUpperCase().equals(fromAddress.toUpperCase()) ) {
                            emailID = email.getEmailId();
                            System.out.println("Found email with ID: " + emailID);
                            break;
                        }
                    }
                    if(emailID == -1){
                        System.out.println("No suitable email was found.");
                    }
                    else {
                        System.out.println("Picking up email with ID: " + emailID);
                        agent.pickUpEmail(emailID, true);
                        System.out.println("Picked up email with ID: " + emailID);
                    }
                    //System.out.println("Sleeping got 10 seconds");
                    //Thread.sleep(10000);
                    //System.out.println("Waking up");
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    work = false;
                }
            }
            System.exit(0);

        } catch (MalformedURLException ex) {
            System.err.println("Exception: " + ex.getMessage());
        } catch (ISException ex) {
            System.err.println("Exception: " + ex.getMessage());
        } catch (ConfigurationException ex) {
            System.err.println("Exception: " + ex.getMessage());
        } catch (RemoteException ex) {
            System.err.println("Exception: " + ex.getMessage());
        } finally

        {
            if (sc != null)
                sc.close();
        }

    }

    static void agentOnIncomingSession(IncomingSessionEvent event) {
        try {
            Agent agent = (Agent) event.getSource();
            //DataInteraction dataInteraction = agent.getDataInteraction(event.getInteractionId());
            Session session = agent.getSession(event.getSessionId());
            System.out.println("Got session");
            EmailMedia[] medias = session.getEmailMedias();
            if (medias != null && medias.length > 0) {
                EmailMedia email = medias[0];
                if (email.getAllowedOperations().getAnswer()) {
                    email.answer(true);
                    System.out.println("Answered email interaction");
                }
            }
            //dataInteraction.
            if (session.getHasDataTransaction()) {
                DataTransaction transaction = session.getDataTransaction();

                if (transaction.getAllowedOperations().getEnd()) {
                    System.out.println("Interactions to cleanup: " + counter);
                    counter--;
                    if (counter == 0) w.setNotReady("Break", true);
                    transaction.end(true);
                    System.out.println("Ended transaction");
                }
            }


            if (counter <= 0) {

                w.close();
                System.out.println("Closed campaign");
                agent.getInstance().logout();
                System.out.println("Logged out");
                work = false;
            }
        } catch (Exception ex) {
            System.err.println("Exception: " + ex.getMessage());
        }
    }
}
