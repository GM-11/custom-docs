#include "../include/pipeline.h"
#include <vector>

std::vector<Operation>
transformPipeline(const std::vector<Operation> &operations,
                  const Operation &incomingOperation) {

  std::vector<Operation> workingSet = std::vector<Operation>{incomingOperation};
  for (Operation op : operations) {
    std::vector<Operation> newSet;
    for (Operation workingOp : workingSet) {
      std::optional<std::vector<Operation>> result =
          performTrasnsformation(op, workingOp);
      if (result.has_value()) {
        for (Operation resOp : result.value()) {
          newSet.push_back(resOp);
        }
      }
    }
    workingSet = newSet;
  }

  return workingSet;
}
