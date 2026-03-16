#pragma once
#include "operation.h"
#include <optional>
#include <vector>

std::optional<Operation> composeTransformation(const Operation &op1,
                                               const Operation &op2);
