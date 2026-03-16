#include <bits/stdc++.h>
#include <iostream>

int main() {
  std::cout << "Hello, World!" << std::endl;
  return 0;
}

/*
    ### Insert vs Insert
    A inserts n chars at p, B inserts at q:
    - q >= p → B's position = **q + n**
    - q < p → B's position **unchanged**

    ---

    ### Delete vs Delete
    A deletes n chars at p, B deletes at q:
    - q > p → B's position = **q - n**
    - q < p → B's position **unchanged**
    - q falls inside A's deleted range → B is **discarded (no-op)**

    // ---

    ### Insert vs Delete
    A inserts n chars at p, B deletes range starting at q:
    - A's insert is **after** B's delete range → B **unchanged**
    - A's insert is **before** B's delete range → B's position = **q + n**
    - A's insert is **inside** B's delete range → B's delete **splits**,
   skipping over inserted content

    ---

    ### Delete vs Insert
    A deletes n chars at p, B inserts at q:
    - q > p → B's position = **q - n**
    - q < p → B's position **unchanged**
    - q falls inside A's deleted range → B's insert position **snaps to p** (the
   deletion point)
    */
