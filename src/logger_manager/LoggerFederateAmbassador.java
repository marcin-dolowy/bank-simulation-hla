package logger_manager;

import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.time.HLAfloat64Time;
import org.portico.impl.hla1516e.types.encoding.HLA1516eInteger32BE;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class LoggerFederateAmbassador extends NullFederateAmbassador {
    private LoggerFederate federate;

    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    protected boolean isRunning = true;

    public LoggerFederateAmbassador(LoggerFederate federate) {
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
        if (label.equals(LoggerFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    @Override
    public void federationSynchronized(String label, FederateHandleSet failed) {
        log("Federation Synchronized: " + label);
        if (label.equals(LoggerFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
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
        throw new NotImplementedException();
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
        StringBuilder builder = new StringBuilder("Interaction Received:");
        builder.append(" handle=").append(interactionClass);

        if (interactionClass.equals(federate.addCustomer)) {
            builder.append(" (AddCustomer)");
        } else if (interactionClass.equals(federate.assignCustomerToQueue)) {
            builder.append(" (AssignCustomerToQueue)");
        } else if (interactionClass.equals(federate.currentQueueSize)) {
            builder.append(" (CurrentQueueSize)");
            return;
        } else if (interactionClass.equals(federate.customerChangeQueue)) {
            builder.append(" (CustomerChangeQueue)");
        } else if (interactionClass.equals(federate.moveCustomerToWindow)) {
            builder.append(" (MoveCustomerToWindow)");
        } else if (interactionClass.equals(federate.assignCustomerToWindow)) {
            builder.append(" (AssignCustomerToWindow)");
        }

        builder.append(", tag=").append(new String(tag));

        if (time != null) {
            builder.append(", time=").append(((HLAfloat64Time) time).getValue());
        }

        builder.append(", parameterCount=").append(theParameters.size()).append("\n");

        for (ParameterHandle parameter : theParameters.keySet()) {
            builder.append("\tparamHandle=").append(parameter);
            byte[] value = theParameters.get(parameter);
            builder.append(", paramValue=").append(value.length).append(" bytes");

            // Specific processing based on parameter handle
            try {
                if (parameter.equals(federate.addCustomerInteractionCustomerId) ||
                        parameter.equals(federate.assignCustomerToQueueCustomerId) ||
                        parameter.equals(federate.customerChangeQueueCustomerId) ||
                        parameter.equals(federate.assignCustomerToWindowCustomerId)) {
                    HLAinteger32BE id = new HLA1516eInteger32BE();
                    id.decode(value);
                    builder.append(" (Customer ID=").append(id.getValue()).append(")");
                } else if (parameter.equals(federate.assignCustomerToQueueQueueId) ||
                        parameter.equals(federate.currentQueueSizeQueueId) ||
                        parameter.equals(federate.customerChangeQueueQueueId)) {
                    HLAinteger32BE id = new HLA1516eInteger32BE();
                    id.decode(value);
                    builder.append(" (Queue ID=").append(id.getValue()).append(")");
                } else if (parameter.equals(federate.moveCustomerToWindowWindowId) ||
                        parameter.equals(federate.assignCustomerToWindowWindowId)) {
                    HLAinteger32BE id = new HLA1516eInteger32BE();
                    id.decode(value);
                    builder.append(" (Window ID=").append(id.getValue()).append(")");
                } else if (parameter.equals(federate.currentQueueSizeSize)) {
                    HLAinteger32BE size = new HLA1516eInteger32BE();
                    size.decode(value);
                    builder.append(" (Queue Size=").append(size.getValue()).append(")");
                }
            } catch (DecoderException e) {
                builder.append(" Error decoding parameter: ").append(e.getMessage());
            }

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
