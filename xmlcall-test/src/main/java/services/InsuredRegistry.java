package services;
import com.google.protobuf.InvalidProtocolBufferException;
import com.luxoft.uhg.fabric.proto.InsuredRegistry.*;

public class InsuredRegistry
{
    public byte[] GetAllMembers(byte[][] args) throws InvalidProtocolBufferException {
        String members[] = {"USER1", "USER2"};
        final MemberList.Builder memberListBuilder = MemberList.newBuilder();

        for (String member : members) {
            final Member m = Member.newBuilder()
                    .setId(member)
                    .build();

            memberListBuilder.addMembers(m);
        }

        return memberListBuilder.build().toByteArray();
    }
}
