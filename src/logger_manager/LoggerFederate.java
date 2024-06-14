package logger_manager;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;

public class LoggerFederate {
    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private LoggerFederateAmbassador fedamb;  // created when we connect
    private HLAfloat64TimeFactory timeFactory; // set when we join
    protected EncoderFactory encoderFactory;     // set when we join
    protected Logger logger = new Logger();

    protected InteractionClassHandle addCustomer;
    protected ParameterHandle addCustomerInteractionCustomerId;
    protected InteractionClassHandle assignCustomerToQueue;
    protected ParameterHandle assignCustomerToQueueCustomerId;
    protected ParameterHandle assignCustomerToQueueQueueId;
    protected InteractionClassHandle currentQueueSize;
    protected ParameterHandle currentQueueSizeQueueId;
    protected ParameterHandle currentQueueSizeSize;
    protected InteractionClassHandle customerChangeQueue;
    protected ParameterHandle customerChangeQueueCustomerId;
    protected ParameterHandle customerChangeQueueQueueId;
    protected InteractionClassHandle moveCustomerToWindow;
    protected ParameterHandle moveCustomerToWindowWindowId;
    protected InteractionClassHandle assignCustomerToWindow;
    protected ParameterHandle assignCustomerToWindowCustomerId;
    protected ParameterHandle assignCustomerToWindowWindowId;


    private void log(String message) {
        System.out.println("LoggerFederate   : " + message);
    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runFederate(String federateName) throws Exception {
        log("Creating RTIambassador");
        rtiamb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
        encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();

        // connect
        log("Connecting...");
        fedamb = new LoggerFederateAmbassador(this);
        rtiamb.connect(fedamb, CallbackModel.HLA_EVOKED);

        log("Creating Federation...");
        try {
            URL[] modules = new URL[]{
                    (new File("foms/BankSimulation.xml")).toURI().toURL(),
            };

            rtiamb.createFederationExecution("BankSimulationFederation", modules);
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception loading one of the FOM modules from disk: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        rtiamb.joinFederationExecution(federateName,            // name for the federate
                "logger",   // federate type
                "BankSimulationFederation"     // name of federation
        );           // modules we want to add

        log("Joined Federation as " + federateName);

        // cache the time factory for easy access
        this.timeFactory = (HLAfloat64TimeFactory) rtiamb.getTimeFactory();

        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);
        // wait until the point is announced
        while (fedamb.isAnnounced == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }

        enableTimePolicy();
        log("Time Policy Enabled");

        publishAndSubscribe();
        log("Published and Subscribed");

        while (fedamb.isRunning) {
            advanceTime(1.0);
            log("Time Advanced to " + fedamb.federateTime);
        }

        rtiamb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        log("Resigned from Federation");

        try {
            rtiamb.destroyFederationExecution("ExampleFederation");
            log("Destroyed Federation");
        } catch (FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (FederatesCurrentlyJoined fcj) {
            log("Didn't destroy federation, federates still joined");
        }
    }

    private void enableTimePolicy() throws Exception {
        HLAfloat64Interval lookahead = timeFactory.makeInterval(fedamb.federateLookahead);
        this.rtiamb.enableTimeRegulation(lookahead);
        while (fedamb.isRegulating == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
        this.rtiamb.enableTimeConstrained();
        while (fedamb.isConstrained == false) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private void publishAndSubscribe() throws RTIexception {
        this.addCustomer = injectSubscribeInteraction("HLAinteractionRoot.addCustomer",
                "addCustomerInteractionCustomerId", null,
                "customerId", null);

        this.assignCustomerToQueue = injectSubscribeInteraction("HLAinteractionRoot.assignCustomerToQueue",
                "assignCustomerToQueueCustomerId", "assignCustomerToQueueQueueId",
                "customerId", "queueId");

        this.currentQueueSize = injectSubscribeInteraction("HLAinteractionRoot.currentQueueSize",
                "currentQueueSizeQueueId", "currentQueueSizeSize",
                "queueId", "size");

        this.customerChangeQueue = injectSubscribeInteraction("HLAinteractionRoot.customerChangeQueue",
                "customerChangeQueueCustomerId", "customerChangeQueueQueueId", "customerId", "queueId");

        this.moveCustomerToWindow = injectSubscribeInteraction("HLAinteractionRoot.moveCustomerToWindow",
                "moveCustomerToWindowWindowId", null,
                "windowId", null);

        this.assignCustomerToWindow = injectSubscribeInteraction("HLAinteractionRoot.assignCustomerToWindow",
                "assignCustomerToWindowCustomerId", "assignCustomerToWindowWindowId",
                "customerId", "windowId");
    }

    private InteractionClassHandle injectSubscribeInteraction(String iname,
                                            String  paramName1, String  paramName2,
                                            String paramNameValue1, String paramNameValue2) throws RTIexception {
        InteractionClassHandle interactionClass = rtiamb.getInteractionClassHandle(iname);
        if (paramNameValue1 != null) {
            setProperty(paramName1, rtiamb.getParameterHandle(rtiamb.getInteractionClassHandle(iname), paramNameValue1));
        }
        if (paramNameValue2 != null) {
            setProperty(paramName2, rtiamb.getParameterHandle(rtiamb.getInteractionClassHandle(iname), paramNameValue2));
        }
        rtiamb.subscribeInteractionClass(interactionClass);
        return interactionClass;
    }

    private void setProperty(String fieldName, Object value) {
        try {
            Class<?> clazz = getClass();
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(this, value);
        } catch (NoSuchFieldException e) {
            System.out.println("Nie znaleziono pola: " + e.getMessage());
        } catch (IllegalAccessException e) {
            System.out.println("Brak dostępu do pola: " + e.getMessage());
        }
    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        HLAfloat64Time time = timeFactory.makeTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(time);
        while (fedamb.isAdvancing) {
            rtiamb.evokeMultipleCallbacks(0.1, 0.2);
        }
    }

    private byte[] generateTag() {
        return ("(timestamp) " + System.currentTimeMillis()).getBytes();
    }

    public static void main(String[] args) {
        // get a federate name, use "exampleFederate" as default
        String federateName = "Logger";
        if (args.length != 0) {
            federateName = args[0];
        }
        try {
            new LoggerFederate().runFederate(federateName);
        } catch (Exception rtie) {
            rtie.printStackTrace();
        }
    }
}