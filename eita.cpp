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

inline void process_command(VProgramCounter* top,
                            Command command,
                            long command_value) {
    switch(command) {
    case PCNext: {
        printf(">>> PCNEXT\n");
        top->PCNext = command_value;
        break;
    }
    case Clk: {
        printf(">>> CLK\n");
        top->Clk = command_value;
        break;
    }
    case PCWrite: {
        printf(">>> PCWRITE\n");
        top->PCWrite = command_value;
        break;
    }
    case Eval: {
        printf(">>> EVAL\n");
        top->eval();
        break;
    }
    }
}

int main(int argc, char** argv, char** env) {
    Verilated::commandArgs(argc, argv);
    VProgramCounter* top = new VProgramCounter();

    std::string op_delimiter = ":";
    std::string op_cmd;
    std::string op_value;

    top->Reset = 0;
    printf("Reset: %d\n", top->Reset);

    long long last_pos = -1;

    std::string line;

    while (!Verilated::gotFinish()) {
        std::ifstream fifo ("caramba.txt", std::ifstream::in);
        fifo.seekg((last_pos + 1) * 32, std::ios::beg);

        if (fifo.is_open())
        {
            while (getline(fifo,line))
            {
                if(line.length() != 32) {
                    continue;
                }

                getline (fifo,line);
                ++last_pos;
                if (line == "") {
                    continue;
                }

                printf("last_pos: %i\n", int(last_pos));

                op_cmd = line.substr(0, line.find(op_delimiter));
                op_value = line.substr(line.find(op_delimiter) + 1);

                std::cout << "cmd " << op_cmd << ": " << op_value << std::endl;

                std::string::size_type sz;
                Command command = static_cast<Command>(std::stol(op_cmd, &sz));
                long command_value = std::stol(op_value, &sz);
                const std::clock_t begin_time = clock();
                process_command(top, command, command_value);
                std::cout << float(clock () - begin_time ) /  CLOCKS_PER_SEC << std::endl;

                printf("PCNext: %i\n", top->PCNext);
                printf("PCWrite: %i\n", top->PCWrite);
                printf("Clk: %i\n", top->Clk);

                printf("PCResult: %i\n", top->PCResult);
                fifo.seekg((last_pos + 1) * 32, std::ios::beg);
                printf("====================\n");
            }
            fifo.close();
        }
    }

    delete top;
    exit(0);
}
