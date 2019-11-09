#include "VProgramCounter.h"
#include "verilated.h"
#include <iostream>
#include <fstream>
#include <thread>
#include <ctime>
#include <map>
#include <functional>
#include <string>

int main(int argc, char** argv, char** env) {
    Verilated::commandArgs(argc, argv);

    vluint8_t VProgramCounter::*pmd = &VProgramCounter::Clk;
    //propmap props;
    //props["name"] = &Foo::name;

    VProgramCounter* top = new VProgramCounter();
    long long last_pos = -1;

    std::string::size_type sz;

    top->Reset = std::stol("0", &sz);
    printf("Reset: %d\n", top->Reset);
    printf("Clk %d\n", top->*pmd);

    while (!Verilated::gotFinish()) {
        std::ifstream myfile ("jjj.txt");
        std::string line;

        std::clock_t begin = clock();
        if (myfile.is_open())
        {
            while (myfile.good())
            {
                getline (myfile,line);
                printf("%s\n", line.c_str());
                ++last_pos;
            }
            myfile.close();
        }
        std::clock_t end = clock();
        double elapsed_secs = double(end - begin) / CLOCKS_PER_SEC;
        printf("\n\nSECS_1: %f\n\n", elapsed_secs);

        top->PCNext += 1;
        top->Clk += 1;
        //top->PCWrite = !top->PCWrite;
        begin = clock();
        top->eval();
        end = clock();
        elapsed_secs = double(end - begin) / CLOCKS_PER_SEC;
        printf("\n\nSECS_2: %f\n\n", elapsed_secs);
        //printf("%i\n", top->PCResult);
    }

    delete top;
    exit(0);
}
