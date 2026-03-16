#pragma once

#include "operation.h"
#include <optional>
#include <vector>
std::optional<std::vector<Operation>>
performTrasnsformation(const Operation &op1, const Operation &op2);
