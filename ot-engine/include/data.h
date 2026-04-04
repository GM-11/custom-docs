#pragma once
#include <optional>
#include <string>
#include <variant>

struct InsertData {
  std::string text;
};

struct DeleteData {
  int length;
};

struct Transform {
  std::optional<std::variant<InsertData, DeleteData>> operation;
  int position;
  int contentVersion;
};
