#include "VProgramCounter.h"
#include "verilated.h"
#include <iostream>
#include <fstream>
#include <chrono>
#include <thread>

int main(int argc, char** argv, char** env) {
    Verilated::commandArgs(argc, argv);
    VProgramCounter* top = new VProgramCounter();
    long long last_pos = -1;

    top->Reset = 0;

    while (!Verilated::gotFinish()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1000));

        std::ifstream myfile ("example.txt");
        std::string line;
        if (myfile.is_open())
        {
            for(int i = 0; i <= last_pos; i++) {
                getline (myfile,line);
            }
            while (myfile.good())
            {
                getline (myfile,line);
                printf("%s\n", line.c_str());
                ++last_pos;
            }
            myfile.close();
        }

        top->PCNext += 1;
        top->Clk += 1;
        top->PCWrite = !top->PCWrite;
        top->eval();
        printf("%i\n", top->PCResult);
    }

    delete top;
    exit(0);
}
