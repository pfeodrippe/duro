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

enum Output {
    OutputALUResult,
    OutputZero
};

extern "C"
inline int process_command(VALU32Bit* top,
                           Command command,
                           long command_value) {
    switch(command) {
    case ALUControl: {
        top->ALUControl = command_value;
        return top->ALUControl;
    }
    case A: {
        top->A = command_value;
        return top->A;
    }
    case B: {
        top->B = command_value;
        return top->B;
    }
    case Eval: {
        printf(">>> Eval\n");
        top->eval();
        return top->ALUResult;
    }
    }
}

extern "C"
VALU32Bit* create_module() {
    char *args[] = {
        NULL
    };

    Verilated::commandArgs(0, args);
    return new VALU32Bit();
}

extern "C"
int* read_module(VALU32Bit* top) {
    static int m[2];
    m[0] = top->ALUResult;
    m[1] = top->Zero;
    return m;
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

    std::ofstream fifo_out ("caramba.txt");
    fifo_out << "";
    fifo_out.close();

    while (!Verilated::gotFinish()) {
        std::ifstream fifo ("caramba.txt", std::ifstream::in);
        std::ofstream outfile ("verilator-writer.txt", std::ios_base::app);
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
                op_cmd = line.substr(0, line.find(op_delimiter));
                op_value = line.substr(line.find(op_delimiter) + 1);

                std::string::size_type sz;
                Command command = static_cast<Command>(std::stol(op_cmd, &sz));
                long command_value = std::stol(op_value, &sz);
                const std::clock_t begin_time = clock();
                process_command(top, command, command_value);
                std::cout << float(clock () - begin_time ) /  CLOCKS_PER_SEC
                          << std::endl;

                if (command == Eval) {
                    outfile
                        << OutputALUResult << ":" << int(top->ALUResult)
                        << " "
                        << OutputZero << ":" << int(top->Zero);

                    printf("ALUControl: %i\n", top->ALUControl);
                    printf("A: %i\n", top->A);
                    printf("B: %i\n", top->B);

                    printf("Zero: %i\n", top->Zero);
                    printf("ALUResult: %i\n", top->ALUResult);
                    printf("====================\n");
                }
            }
            fifo.close();
            outfile.close();
        }
    }

    delete top;
    exit(0);
}
