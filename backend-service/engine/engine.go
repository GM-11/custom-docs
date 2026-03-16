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
	Type     int
	Position int
	Version  int
	ClientID string
	Data     OperationData // can hold either string or int
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

	if goOp.Type == INSERT {
		C.free(unsafe.Pointer(*(**C.char)(unsafe.Pointer(&cOp.data))))
	}

	return goOp
}

func PerformTransformation(op1, op2 Operation) Operation {
	cOp1 := goOperationToC(op1)
	cOp2 := goOperationToC(op2)

	var flag C.int = 2
	result := C.performTransformation(&cOp1, &cOp2, &flag)

	defer C.free(unsafe.Pointer(result.clientId))
	if result._type == INSERT {
		defer C.free(unsafe.Pointer(*(**C.char)(unsafe.Pointer(&result.data))))
	}

	return cOperationToGo(*result)
}

func ApplyOperation(doc string, op Operation) string {

	cDoc := C.CString(doc)
	defer C.freeDocument(cDoc)
	cOp := goOperationToC(op)

	result := C.applyTransformations(cDoc, &cOp)

	defer C.free(unsafe.Pointer(result))
	defer C.free(unsafe.Pointer(cOp.clientId))
	return C.GoString(result)

}
func TransformPipeline(operations []Operation, incomingOperation Operation) []Operation {
	cOps := make([]C.OperationC, len(operations))
	for i, op := range operations {
		cOps[i] = goOperationToC(op)
	}
	defer func() {
		for i, op := range operations {
			if op.Type == INSERT {
				C.free(unsafe.Pointer(*(**C.char)(unsafe.Pointer(&cOps[i].data))))
			}
			C.free(unsafe.Pointer(cOps[i].clientId))
		}
	}()

	cIncomingOp := goOperationToC(incomingOperation)
	defer C.free(unsafe.Pointer(cIncomingOp.clientId))
	if incomingOperation.Type == INSERT {
		defer C.free(unsafe.Pointer(*(**C.char)(unsafe.Pointer(&cIncomingOp.data))))
	}

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
