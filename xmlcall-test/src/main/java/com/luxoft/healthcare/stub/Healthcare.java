package com.luxoft.healthcare.stub;

import com.google.protobuf.InvalidProtocolBufferException;
import com.luxoft.healthcare.messages.HealthcareMessages;
import java.util.UUID;

/* it's a stub to be used with XmlCallReflectionConnector */

public class Healthcare
{
    public byte[] GetAccumulator(byte[][] args) throws InvalidProtocolBufferException {
        final byte[] msg = args[0];
        try {
            final HealthcareMessages.GetAccumulator req = HealthcareMessages.GetAccumulator.parseFrom(msg);
            return HealthcareMessages.Accumulator.newBuilder()
                    .setMemberId(req.getMemberId())
                    .setAccumulatorId(req.getAccumulatorId())
                    .setPlanYear(req.getPlanYear())
                    .setStateHash(UUID.randomUUID().toString())
                    .setValueCents(100)
                    .build()
                    .toByteArray();
        }

        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public byte[] AddClaim(byte[][] args) throws InvalidProtocolBufferException {
        final byte[] msg = args[0];
        try {
            final HealthcareMessages.AddClaim addClaim = HealthcareMessages.AddClaim.parseFrom(msg);
            final HealthcareMessages.Claim claim = addClaim.getClaim();

            final HealthcareMessages.Accumulator result = HealthcareMessages.Accumulator.newBuilder()
                    .setMemberId(claim.getMemberId())
                    .setAccumulatorId(claim.getAccumulatorId())
                    .setPlanYear(claim.getPlanYear())
                    .setValueCents(claim.getAmountCents())
                    .build();

            return result.toByteArray();
        }

        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
