#include "verilated.h"
#include "generated_template.h"
#include <iostream>
#include <cstdio>
#include "verilated_vcd_c.h"

long long input[INPUT_SIZE];
long long output[OUTPUT_SIZE];
int eval_flags[2];

extern "C"
long long* get_input_pointer() {
    return input;
}

extern "C"
long long* get_output_pointer() {
    return output;
}

extern "C"
int* get_eval_flags_pointer() {
    return eval_flags;
}

extern "C"
void set_local_signal(TOP_CLASS* top, int sig, int arg) {
    GENERATED_LOCAL_SIGNAL_INPUTS
    GENERATED_INDEPENDENT_SIGNAL_INPUTS
}

extern "C"
long long get_local_signal(TOP_CLASS* top, int sig) {
    GENERATED_LOCAL_SIGNAL_OUTPUTS
    GENERATED_INDEPENDENT_SIGNAL_OUTPUTS
    return 10101010; //default
}

extern "C"
void set_array_signal(TOP_CLASS* top, int sig, int idx, int arg) {
    GENERATED_ARRAY_SIGNAL_INPUTS
}

extern "C"
int get_array_signal(TOP_CLASS* top, int sig, int idx) {
    GENERATED_ARRAY_SIGNAL_OUTPUTS
    return 10101010; //default
}

extern "C"
int eval(TOP_CLASS* top) {
    using namespace std;
    FILE *fp;
    fp = fopen("example.txt","w");
    while (eval_flags[1] != 0) {
        if (eval_flags[0] != 0) {
            GENERATED_INPUTS
            top->eval();
            GENERATED_OUTPUTS
            fprintf(fp, "\n\ni_a: %d\n", top->i_a);
            fprintf(fp, "i_b: %d\n", top->i_b);
            fprintf(fp, "o_c: %d\n", top->o_c);
            fprintf(fp, "i_clk: %d\n", top->i_clk);
            fprintf(fp, "o_busy: %d\n", top->o_busy);
            fprintf(fp, "o_valid: %d\n\n", top->o_valid);
            fprintf(fp, "---------------------------");
            eval_flags[0] = 0;
        }
    }
    fclose(fp);
    return 9999;
}

extern "C"
void only_eval(TOP_CLASS* top) {
    top->eval();
}

extern "C"
TOP_CLASS* create_module() {
    char *args[] = {
        NULL
    };

    eval_flags[0] = 0;
    eval_flags[1] = 1;
    Verilated::commandArgs(0, args);
    return new TOP_CLASS();
}





uint64_t m_tickcount;

void tick(TOP_CLASS* top, VerilatedVcdC* m_trace) {
    m_tickcount++;
    top->eval();
    m_trace->dump((vluint64_t)(10*m_tickcount-2));
    top->i_clk = 1;
    top->eval();
    m_trace->dump((vluint64_t)(10*m_tickcount));
    top->i_clk = 0;
    top->eval();
    m_trace->dump((vluint64_t)(10*m_tickcount+5));
    m_trace->flush();
}

void reset(TOP_CLASS* top, VerilatedVcdC* m_trace) {
    top->i_stb = 0;
    top->i_reset = 1;
    tick(top, m_trace);
    top->i_reset = 0;
    // printf("RESET\n");
}

void clear_ops (TOP_CLASS* top, VerilatedVcdC* m_trace) {
    top->i_stb = 0;
    top->i_op = 0;
    do {
        tick(top, m_trace);
    } while((top->o_busy)||(top->o_valid));
    tick(top, m_trace);
}

int main(int argc, char** argv, char** env) {
    //exit(1);
    Verilated::commandArgs(argc, argv);
    TOP_CLASS* top = new TOP_CLASS();
    m_tickcount = 0;

    Verilated::traceEverOn(true);
    top->i_clk = 0;
    top->eval();

    VerilatedVcdC* m_trace = new VerilatedVcdC();
    top->trace(m_trace, 99);
    m_trace->open("jjj.vcd");

    tick(top, m_trace);
    reset(top, m_trace);
    tick(top, m_trace);
    tick(top, m_trace);
    tick(top, m_trace);

    clear_ops(top, m_trace);

    top->i_stb    = 1;
    top->i_op    = 0x0b;
    top->i_a     = 3;
    top->i_b     = 5;

    tick(top, m_trace);

    top->i_stb    = 0;
    top->i_a     = 0;
    top->i_b     = 0;

    while(!top->o_valid)
        tick(top, m_trace);

    //clear_ops(top, m_trace);

    tick(top, m_trace);
    tick(top, m_trace);
    tick(top, m_trace);
    tick(top, m_trace);
    tick(top, m_trace);

    m_trace->close();
}
