#include "../include/apply.h"
#include <stdexcept>

std::string applyTransformations(std::string currentDocument,
                                 Operation latestOperation) {

  int currentDocumentLength = currentDocument.size();

  switch (latestOperation.type) {
  case OperationType::INSERT: {
    InsertData data = std::get<InsertData>(latestOperation.data);
    if (currentDocumentLength >= latestOperation.position)
      currentDocument.insert(latestOperation.position, data.text);
    else
      currentDocument.append(data.text);
    break;
  }
  case OperationType::DELETE: {
    DeleteData data = std::get<DeleteData>(latestOperation.data);
    if (currentDocumentLength > latestOperation.position &&
        currentDocumentLength >= latestOperation.position + data.length)
      currentDocument.erase(latestOperation.position, data.length);
    else
      throw std::out_of_range("Position exceeds document length");
  }
  }

  return currentDocument;
};
