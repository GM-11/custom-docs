package engine

// #cgo LDFLAGS: -L/home/gm11/codes/custom-docs/ot-engine/build -lot-engine-lib -Wl,-rpath,/home/gm11/codes/custom-docs/ot-engine/build
// #include "/home/gm11/codes/custom-docs/ot-engine/ffi/ot_ffi.h"
// #include <stdlib.h>
import "C"
import (
	"unsafe"
)

type OperationData interface{}

type Operation struct {
	Type     int           `json:"type"`
	Position int           `json:"position"`
	Version  int           `json:"version"`
	ClientID string        `json:"clientId"`
	Data     OperationData `json:"data"`
}

const (
	INSERT = 0
	DELETE = 1
)

func goOperationToC(goOp Operation) C.OperationC {
	op := C.OperationC{}
	op._type = C.int(goOp.Type)
	op.position = C.int(goOp.Position)
	op.version = C.int(goOp.Version)
	op.clientId = C.CString(goOp.ClientID)

	switch v := goOp.Data.(type) {
	case string:
		*(**C.char)(unsafe.Pointer(&op.data)) = C.CString(v)

	case int:
		*(*C.int)(unsafe.Pointer(&op.data)) = C.int(v)

	case float64:
		*(*C.int)(unsafe.Pointer(&op.data)) = C.int(int(v))

	default:
		panic("unsupported data type")
	}

	return op
}

func cOperationToGo(cOp C.OperationC) Operation {
	goOp := Operation{
		Type:     int(cOp._type),
		Position: int(cOp.position),
		Version:  int(cOp.version),
		ClientID: C.GoString(cOp.clientId),
	}

	switch cOp._type {
	case INSERT: // insert
		goOp.Data = C.GoString((*(**C.char)(unsafe.Pointer(&cOp.data))))

	case DELETE: // delete
		goOp.Data = int(*(*C.int)(unsafe.Pointer(&cOp.data)))

	default:
		panic("unsupported operation type")
	}

	return goOp
}

func freeCOperation(cOp C.OperationC, opType int) {
	C.free(unsafe.Pointer(cOp.clientId))
	if opType == INSERT {
		C.free(unsafe.Pointer(*(**C.char)(unsafe.Pointer(&cOp.data))))
	}
}

func PerformTransformation(op1, op2 Operation) Operation {
	cOp1 := goOperationToC(op1)
	cOp2 := goOperationToC(op2)

	defer C.free(unsafe.Pointer(cOp1.clientId))
	defer C.free(unsafe.Pointer(cOp2.clientId))

	var flag C.int = 2
	result := C.performTransformation(&cOp1, &cOp2, &flag)
	if result == nil {
		return op2
	}
	goResult := cOperationToGo(*result)

	freeCOperation(*result, int(result._type))
	return goResult
}

func ApplyOperation(doc string, op Operation) string {

	cDoc := C.CString(doc)
	defer C.freeDocument(cDoc)
	cOp := goOperationToC(op)
	defer freeCOperation(cOp, op.Type)
	result := C.applyTransformations(cDoc, &cOp)

	defer C.free(unsafe.Pointer(result))
	return C.GoString(result)

}
func TransformPipeline(operations []Operation, incomingOperation Operation) []Operation {
	cOps := make([]C.OperationC, len(operations))
	for i, op := range operations {
		cOps[i] = goOperationToC(op)
	}
	defer func() {
		for i, op := range operations {
			freeCOperation(cOps[i], op.Type)
		}
	}()

	cIncomingOp := goOperationToC(incomingOperation)
	defer freeCOperation(cIncomingOp, incomingOperation.Type)

	opsCount := C.int(len(operations))
	resultCount := C.int(0)
	result := C.transformPipeLine((*C.OperationC)(unsafe.Pointer(&cOps[0])), &cIncomingOp, &resultCount, &opsCount)

	var transformedOps []Operation
	if resultCount > 0 {
		resultSlice := (*[1 << 30]C.OperationC)(unsafe.Pointer(result))[:resultCount:resultCount]
		for i := 0; i < int(resultCount); i++ {
			transformedOps = append(transformedOps, cOperationToGo(resultSlice[i]))
		}
		C.freeOperations(result, resultCount)
	}

	return transformedOps
}
