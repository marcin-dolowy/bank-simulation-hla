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
        String timeAsString = time != null ? String.valueOf(((HLAfloat64Time) time).getValue()) : "";

        StringBuilder attributesMapAsString = new StringBuilder();

        String interactionName = "";
        if (interactionClass.equals(federate.moveCustomerToWindow)) {
            interactionName = "(Customer)";

            for (ParameterHandle parameter : theParameters.keySet()) {
                // TUTJA SPRAWDZIC
                byte[] bytes = theParameters.get(federate.freeWindowID);
                HLAinteger32BE windowId = new HLA1516eInteger32BE();
                try {
                    windowId.decode(bytes);
                } catch (DecoderException e) {
                    e.printStackTrace();
                }
                int customerIdValue = windowId.getValue();
                String paramValue = String.valueOf(customerIdValue);

                attributesMapAsString.append(String.format("paramHandle=%s, paramValueInBytes=%s, paramValue=%s \n", parameter, theParameters.get(parameter).length, paramValue));
            }

            log(String.format("handle=%s, interactionName=%s, tag=%s, time=%s, attributesMapSize=%s, attributesMap=%s",
                    interactionClass, interactionName, new String(tag), timeAsString, theParameters.size(), attributesMapAsString));
        }
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
