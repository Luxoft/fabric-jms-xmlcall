package main

import (
	"github.com/hyperledger/fabric/core/chaincode/shim"
	pb "github.com/hyperledger/fabric/protos/peer"
	"fmt"
	"github.com/golang/protobuf/proto"
	//"crypto/sha256"
	//"encoding/hex"
	"chaincode/proto/messages"
)

var logger = shim.NewLogger("Healthcare")

type Healthcare struct {
}

func (t *Healthcare) Init(stub shim.ChaincodeStubInterface) pb.Response {

	logger.Info("Init")

	return shim.Success(nil)
}


func (t *Healthcare) Invoke(stub shim.ChaincodeStubInterface) pb.Response {


	function, args := stub.GetFunctionAndParameters()

	logger.Info("Invoke: " + function)

	switch function {

	case "AddClaim":

		if len(args) < 1 {
			return loggedShimError(fmt.Sprintf("Insufficient arguments number\n"))
		}

		req := new(hc.AddClaim)
		if err := proto.Unmarshal([]byte(args[0]), req); err != nil {
			return loggedShimError(fmt.Sprintf("Invalid argument expected AddClaim protocol buffer %s\n", err.Error()))
		}

		ref, err := t.addClaim(stub, req)

		if err != nil {
			return loggedShimError(fmt.Sprintf("AddClaim: Error %s\n", err.Error()))
		}

		pbmessage, err := proto.Marshal(ref)
		if err != nil {
			return loggedShimError(fmt.Sprintf("Failed to marshal Allowed protobuf (%s)", err.Error()))
		}

		return shim.Success(pbmessage)

	case "GetAccumulator":

		if len(args) < 1 {
			return loggedShimError(fmt.Sprintf("Insufficient arguments number\n"))
		}

		req := new(hc.GetAccumulator)
		if err := proto.Unmarshal([]byte(args[0]), req); err != nil {
			return loggedShimError(fmt.Sprintf("Invalid argument expected GetAccumulator protocol buffer %s\n", err.Error()))
		}

		ref, err := t.getAccumulator(stub, req)

		if err != nil {
			return loggedShimError(fmt.Sprintf("Error getting message: %s\n", err.Error()))
		}

		pbmessage, err := proto.Marshal(ref)
		if err != nil {
			return loggedShimError(fmt.Sprintf("Failed to marshal Allowed protobuf (%s)", err.Error()))
		}

		return shim.Success(pbmessage)

	}

	return loggedShimError("Invalid invoke function name. Expecting \"invoke\" \"getBalance\"")
}



func (t *Healthcare) loadAccumulator(stub shim.ChaincodeStubInterface, memberId string, accumulatorId string, planYear int32) (*hc.Accumulator, error) {

	entity := new(hc.Accumulator)
	key := fmt.Sprintf("%v:%v:%v", memberId, accumulatorId, planYear)

	// getting real file descriptor by key
	state, err := stub.GetState(key)
	if err != nil {
		return entity, fmt.Errorf("Error getting entity from db: " + err.Error())
	}

	if state == nil {
		entity = new(hc.Accumulator)
		entity.MemberId = memberId
		entity.AccumulatorId = accumulatorId
		entity.PlanYear = planYear
		entity.ValueCents = 0
	} else {

		if err := proto.Unmarshal(state, entity); err != nil {
			return entity, fmt.Errorf("Error parsing state: " + err.Error())
		}
	}
	return entity, nil
}

func (t *Healthcare) saveAccumulator(stub shim.ChaincodeStubInterface, acc *hc.Accumulator) (error) {

	key := fmt.Sprintf("%v:%v:%v", acc.MemberId, acc.AccumulatorId, acc.PlanYear)

	state, err := proto.Marshal(acc)
	if err != nil {
		return fmt.Errorf("failed to create json for entity <%s> with error: %s" , key, err)
	}

	if err := stub.PutState(key, state); err != nil {
		return fmt.Errorf("failed to store entity <%s> with error: %s" , key, err)
	}
	return nil
}


func (t *Healthcare) addClaim(stubInterface shim.ChaincodeStubInterface, claim *hc.AddClaim) (*hc.Accumulator, error) {

	c := claim.GetClaim()

	s, err := t.loadAccumulator(stubInterface, c.MemberId, c.AccumulatorId, c.PlanYear)

	if err != nil {
		return nil, err
	}

	//// find out state hash
	//bytes, err := proto.Marshal(s)
	//h := sha256.New()
	//h.Write(bytes)
	//currentState := hex.EncodeToString(h.Sum(nil))
	//
	//if currentState != claim.StateHash {
	//	return nil, fmt.Errorf("wrong state hash, expected %s, got %s", currentState, claim.StateHash)
	//}

	// hash is okay
	s.ValueCents += c.AmountCents
	return s, t.saveAccumulator(stubInterface, s)
}


func (t *Healthcare) getAccumulator(stub shim.ChaincodeStubInterface, ref *hc.GetAccumulator) (*hc.Accumulator, error) {

	entity, err := t.loadAccumulator(stub, ref.MemberId, ref.AccumulatorId, ref.PlanYear)

	if err != nil {
		return nil, fmt.Errorf("Error getting file from db: " + err.Error())
	}

	return entity, nil
}

// Convenience method to make sure all errors are logged and to decrease code lines number
func loggedShimError(message string) pb.Response {
	logger.Error(message)
	return shim.Error(message)
}

func main() {
	err := shim.Start(new(Healthcare))
	if err != nil {
		logger.Errorf("Error starting chaincode: %s", err)
	}
}
