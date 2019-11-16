#define GENERATED_INPUTS \
    top->ALUControl = input[ALUControl]; \
    top->A = input[A]; \
    top->B = input[B];

#define GENERATED_OUTPUTS \
    output[ALUResult] = top->ALUResult; \
    output[Zero] = top->Zero;

enum Command {
    ALUControl,
    A,
    B
};

enum Output {
    ALUResult,
    Zero
};
