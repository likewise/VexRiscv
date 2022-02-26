#include <stdint.h>

#include "finka.h"

void print(const char*str){
	while(*str){
		uart_write(UART,*str);
		str++;
	}
}
void println(const char*str){
	print(str);
	uart_write(UART,'\n');
}

void delay(uint32_t loops){
	for(int i=0;i<loops;i++){
		int tmp = GPIO_A->OUTPUT;
	}
}

Uart_Config uart_cfg = {
  .dataLength = 8,
  .parity = 0,
  .stop = 1,
  .clockDivider = 250000000/8/115200-1
};

void main() {
	uart_applyConfig(UART, &uart_cfg);
    println("Hello world! I am Finka.");

	*((volatile uint32_t *)AXI_M1) = 0xaabbccddU;
	*((volatile uint8_t *)AXI_M1 + 0) = (uint8_t)0x11U;
	*((volatile uint8_t *)AXI_M1 + 1) = (uint8_t)0x22U;
	*((volatile uint8_t *)AXI_M1 + 2) = (uint8_t)0x33U;
	*((volatile uint8_t *)AXI_M1 + 3) = (uint8_t)0x44U;
	*((volatile uint32_t *)AXI_M1) = 0xdeadbeefU;

    GPIO_A->OUTPUT_ENABLE = 0x0000000F;
	GPIO_A->OUTPUT = 0x00000001;

    const int nleds = 8;
    const int nloops = 20000;
    timer_init(TIMER_A);
    while(1){
    	for(unsigned int i=0;i<nleds-1;i++){
    		GPIO_A->OUTPUT = 1<<i;
    		delay(nloops);
    	}
    	for(unsigned int i=0;i<nleds-1;i++){
			GPIO_A->OUTPUT = (1<<(nleds-1))>>i;
			delay(nloops);
		}
        println("Hello world! I am Finka again.");
    }
	//	(void)*AXI_M1;
}

void irqCallback(){
	int x = 42;
	int y = x * 4;
	(void)x;
	(void)y;
	//uart_write(UART,'.');
}
