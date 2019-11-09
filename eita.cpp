#include "VProgramCounter.h"
#include "verilated.h"
#include <iostream>
#include <fstream>
#include <thread>
#include <ctime>
#include <map>
#include <functional>
#include <string>

enum Command {
    PCNext,
    Clk,
    Eval,
    PCWrite
};

int main(int argc, char** argv, char** env) {
    Verilated::commandArgs(argc, argv);
    VProgramCounter* top = new VProgramCounter();

    std::string op_delimiter = ":";
    std::string op_cmd;
    std::string op_value;

    top->Reset = 0;
    printf("Reset: %d\n", top->Reset);

    while (!Verilated::gotFinish()) {
        top->eval();
        std::ifstream myfile ("jjj.txt");
        std::string line;

        std::clock_t begin = clock();
        if (myfile.is_open())
        {
            while (myfile.good())
            {
                getline (myfile,line);
                op_cmd = line.substr(0, line.find(op_delimiter));
                op_value = line.substr(line.find(op_delimiter) + 1);

                std::string::size_type sz;

                std::cout << "cmd " << op_cmd << ": " << op_value << std::endl;

                if (line == "") {
                    op_cmd = "9999";
                }

                switch(static_cast<Command>(std::stol(op_cmd, &sz))) {
                case PCNext: {
                    printf(">>> PCNEXT\n");
                    top->PCNext = std::stol(op_value, &sz);
                    break;
                }
                case Clk: {
                    printf(">>> CLK\n");
                    top->Clk = std::stol(op_value, &sz);
                    break;
                }
                case Eval: {
                    printf(">>> EVAL\n");
                    top->eval();
                    break;
                }
                case PCWrite: {
                    printf(">>> PCWRITE\n");
                    top->PCWrite = std::stol(op_value, &sz);
                    break;
                }
                }

                printf("PCNext: %i\n", top->PCNext);
                printf("PCWrite: %i\n", top->PCWrite);
                printf("Clk: %i\n", top->Clk);
            }
            myfile.close();
        }
        std::clock_t end = clock();
        double elapsed_secs = double(end - begin) / CLOCKS_PER_SEC;
        //printf("\n\nSECS_1: %f\n\n", elapsed_secs);

        //top->PCNext += 1;
        //top->Clk += 1;
        //top->PCWrite = !top->PCWrite;
        //begin = clock();
        //top->eval();
        //end = clock();
        //elapsed_secs = double(end - begin) / CLOCKS_PER_SEC;
        //printf("\n\nSECS_2: %f\n\n", elapsed_secs);
        printf("%i\n", top->PCResult);
    }

    delete top;
    exit(0);
}
