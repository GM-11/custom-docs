#pragma once

#include "../include/data.h"
#include "../include/operationType.h"
#include <string>
#include <variant>

typedef struct Operation {
  OperationType type;
  int position;
  std::variant<InsertData, DeleteData> data; // string for add, int for delete
  int version;
  std::string clientId;

  Operation(OperationType type, int position,
            std::variant<InsertData, DeleteData> data, int version,
            const std::string &clientId);
} Operation;
