#include "VProgramCounter.h"
#include "verilated.h"
#include <chrono>
#include <thread>

int main(int argc, char** argv, char** env) {
    Verilated::commandArgs(argc, argv);
    VProgramCounter* top = new VProgramCounter();

    top->Reset = 0;

    while (!Verilated::gotFinish()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        top->PCNext += 1;
        top->Clk += 1;
        top->PCWrite = !top->PCWrite;
        top->eval();
        printf("%i\n", top->PCResult);
    }
    delete top;
    exit(0);
}
