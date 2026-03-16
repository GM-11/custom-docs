#pragma once

#include "operation.h"
#include "transform.h"
#include <vector>
std::vector<Operation>
transformPipeline(const std::vector<Operation> &operations,
                  const Operation &incomingOperation);
