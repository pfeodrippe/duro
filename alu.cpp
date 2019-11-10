#include "VALU32Bit.h"
#include "verilated.h"
#include <iostream>
#include <fstream>
#include <thread>
#include <ctime>
#include <map>
#include <functional>
#include <string>

enum Command {
    ALUControl,
    A,
    B,
    Eval
};

inline void process_command(VALU32Bit* top,
                            Command command,
                            long command_value) {
    switch(command) {
    case ALUControl: {
        printf(">>> ALUControl\n");
        top->ALUControl = command_value;
        break;
    }
    case A: {
        printf(">>> A\n");
        top->A = command_value;
        break;
    }
    case B: {
        printf(">>> B\n");
        top->B = command_value;
        break;
    }
    case Eval: {
        printf(">>> Eval\n");
        top->eval();
        break;
    }
    }
}

int main(int argc, char** argv, char** env) {
    Verilated::commandArgs(argc, argv);
    Verilated::debug(1);
    VALU32Bit* top = new VALU32Bit();

    std::string op_delimiter = ":";
    std::string op_cmd;
    std::string op_value;

    long long last_pos = 0;

    std::string line;

    while (!Verilated::gotFinish()) {
        std::ifstream fifo ("caramba.txt", std::ifstream::in);
        fifo.seekg(last_pos);

        if (fifo.is_open())
        {
            while (getline(fifo,line))
            {
                if(line.length() != 32) {
                    if (line.length() != 0) {
                        std::cout << "!!!!!!! File LEN: " << line.length() << std::endl;
                        std::cout << "!!!!!!! line    : " << line << std::endl;
                    }
                    fifo.seekg(last_pos);
                    continue;
                }

                last_pos = fifo.tellg();
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

                printf("ALUControl: %i\n", top->ALUControl);
                printf("A: %i\n", top->A);
                printf("B: %i\n", top->B);

                printf("Zero: %i\n", top->Zero);
                printf("ALUResult: %i\n", top->ALUResult);
                printf("====================\n");
            }
            fifo.close();
        }
    }

    delete top;
    exit(0);
}
