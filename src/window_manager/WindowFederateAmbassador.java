package window_manager;

import Producer.ProducerFederate;
import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32BE;

public class WindowFederateAmbassador extends NullFederateAmbassador {

    private WindowFederate federate;

    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;


    protected boolean isRunning = true;


    public WindowFederateAmbassador(WindowFederate federate) {
        this.federate = federate;
    }


    private void log(String message) {
        System.out.println("FederateAmbassador: " + message);
    }


    @Override
    public void synchronizationPointRegistrationFailed(String label,
                                                       SynchronizationPointFailureReason reason) {
        log("Failed to register sync point: " + label + ", reason=" + reason);
    }

    @Override
    public void synchronizationPointRegistrationSucceeded(String label) {
        log("Successfully registered sync point: " + label);
    }

    @Override
    public void announceSynchronizationPoint(String label, byte[] tag) {
        log("Synchronization point announced: " + label);
        if (label.equals(ProducerFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(ProducerFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }


    @Override
    public void timeRegulationEnabled(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isRegulating = true;
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isConstrained = true;
    }

    @Override
    public void timeAdvanceGrant(LogicalTime time) {
        this.federateTime = ((HLAfloat64Time) time).getValue();
        this.isAdvancing = false;
    }

    @Override
    public void discoverObjectInstance(ObjectInstanceHandle theObject,
                                       ObjectClassHandle theObjectClass,
                                       String objectName)
            throws FederateInternalError {
        log("Discoverd Object: handle=" + theObject + ", classHandle=" +
                theObjectClass + ", name=" + objectName);
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] tag,
                                       OrderType sentOrder,
                                       TransportationTypeHandle transport,
                                       SupplementalReflectInfo reflectInfo)
            throws FederateInternalError {

        reflectAttributeValues(theObject,
                theAttributes,
                tag,
                sentOrder,
                transport,
                null,
                sentOrder,
                reflectInfo);
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] tag,
                                       OrderType sentOrdering,
                                       TransportationTypeHandle theTransport,
                                       LogicalTime time,
                                       OrderType receivedOrdering,
                                       SupplementalReflectInfo reflectInfo)
            throws FederateInternalError {
        StringBuilder builder = new StringBuilder("Reflection for object:");

        builder.append(" handle=" + theObject);
        builder.append(", tag=" + new String(tag));
        if (time != null) {
            builder.append(", time=" + ((HLAfloat64Time) time).getValue());
        }

        builder.append(", attributeCount=" + theAttributes.size());
        builder.append("\n");
        for (AttributeHandle attributeHandle : theAttributes.keySet()) {
            builder.append("\tattributeHandle=");

            if (attributeHandle.equals(federate.storageAvailableHandle)) {
                builder.append(attributeHandle);
                builder.append(" (Available)    ");
                builder.append(", attributeValue=");
                HLAinteger32BE available = new HLA1516eInteger32BE();
                try {
                    available.decode(theAttributes.get(attributeHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                builder.append(available.getValue());
                federate.storageAvailable = available.getValue();
            } else if (attributeHandle.equals(federate.storageMaxHandle)) {
                builder.append(attributeHandle);
                builder.append(" (Max)");
                builder.append(", attributeValue=");
                HLAinteger32BE max = new HLA1516eInteger32BE();
                try {
                    max.decode(theAttributes.get(attributeHandle));
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                builder.append(max.getValue());
                federate.storageMax = max.getValue();
            } else {
                builder.append(attributeHandle);
                builder.append(" (Unknown)   ");
            }

            builder.append("\n");
        }

        log(builder.toString());
    }

//    @Override
//    public void reflectAttributeValues(ObjectInstanceHandle theObject,
//                                       AttributeHandleValueMap theAttributes,
//                                       byte[] tag,
//                                       OrderType sentOrdering,
//                                       TransportationTypeHandle theTransport,
//                                       LogicalTime time,
//                                       OrderType receivedOrdering,
//                                       SupplementalReflectInfo reflectInfo)
//            throws FederateInternalError {
//        // Construct the initial log message
//        String timeValue = time != null ? ", time=" + ((HLAfloat64Time) time).getValue() : "";
//        StringBuilder message = new StringBuilder(String.format("Reflection for object: handle=%s, tag=%s%s, attributeCount=%d\n",
//                theObject, new String(tag), timeValue, theAttributes.size()));
//
//        // Append details about each attribute
//        for (AttributeHandle attributeHandle : theAttributes.keySet()) {
//            String attributeName = " (Unknown)   "; // Default attribute name
//            int attributeValue = 0; // Default attribute value
//            HLAinteger32BE valueDecoder = new HLA1516eInteger32BE();
//
//            try {
//                valueDecoder.decode(theAttributes.get(attributeHandle));
//                attributeValue = valueDecoder.getValue();
//
//                if (attributeHandle.equals(federate.storageAvailableHandle)) {
//                    attributeName = " (Available)    ";
//                    federate.storageAvailable = attributeValue;
//                } else if (attributeHandle.equals(federate.storageMaxHandle)) {
//                    attributeName = " (Max)";
//                    federate.storageMax = attributeValue;
//                }
//            } catch (DecoderException e) {
//                e.printStackTrace();
//            }
//
//            message.append(String.format("\tattributeHandle=%s%s, attributeValue=%d\n",
//                    attributeHandle, attributeName, attributeValue));
//        }
//
//        // Log the constructed message
//        log(message.toString());
//    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {

        this.receiveInteraction(interactionClass,
                theParameters,
                tag,
                sentOrdering,
                theTransport,
                null,
                sentOrdering,
                receiveInfo);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] tag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   LogicalTime time,
                                   OrderType receivedOrdering,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        StringBuilder builder = new StringBuilder("Interaction Received:");

        builder.append(" handle=" + interactionClass);
        if (interactionClass.equals(federate.freeWindowID)) {
            builder.append(" (DrinkServed)");
        }

        builder.append(", tag=" + new String(tag));
        if (time != null) {
            builder.append(", time=" + ((HLAfloat64Time) time).getValue());
        }

        builder.append(", parameterCount=" + theParameters.size());
        builder.append("\n");
        for (ParameterHandle parameter : theParameters.keySet()) {
            builder.append("\tparamHandle=");
            builder.append(parameter);
            builder.append(", paramValue=");
            builder.append(theParameters.get(parameter).length);
            builder.append(" bytes");
            builder.append("\n");
        }

        log(builder.toString());
    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] tag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo)
            throws FederateInternalError {
        log("Object Removed: handle=" + theObject);
    }

}
