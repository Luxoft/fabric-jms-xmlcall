package services;

import com.google.protobuf.InvalidProtocolBufferException;
import com.luxoft.uhg.fabric.proto.ClaimAccumulator;

import java.util.UUID;

/* it's a stub to be used with XmlCallReflectionConnector */

public class Accumulator
{
    public byte[] GetAccumulator(byte[][] args) throws InvalidProtocolBufferException {
        final byte[] msg = args[0];
        try {
            final ClaimAccumulator.GetAccumulator req = ClaimAccumulator.GetAccumulator.parseFrom(msg);
            return ClaimAccumulator.Accumulator.newBuilder()
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
            final ClaimAccumulator.AddClaim addClaim = ClaimAccumulator.AddClaim.parseFrom(msg);
            final ClaimAccumulator.Claim claim = addClaim.getClaim();

            final ClaimAccumulator.GetAccumulator getAccumulator = ClaimAccumulator.GetAccumulator.newBuilder()
                    .setMemberId(claim.getMemberId())
                    .setAccumulatorId(claim.getAccumulatorId())
                    .setPlanYear(claim.getPlanYear())
                    .build();

            final ClaimAccumulator.AddClaimResponse addClaimResponse = ClaimAccumulator.AddClaimResponse.newBuilder()
                    .setAccRef(getAccumulator)
                    .setWasCollision(false)
                    .setWasLimitReached(false)
                    .build();

            return addClaimResponse.toByteArray();
        }

        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
