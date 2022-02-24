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

void main() {
    //println("Hello world! I am Finka.");
	*((volatile uint32_t *)AXI_M1) = 0xaabbccddU;
	*((volatile uint8_t *)AXI_M1 + 1) = (uint8_t)0x11U;
	*((volatile uint8_t *)AXI_M1 + 2) = (uint8_t)0x22U;
	*((volatile uint8_t *)AXI_M1 + 3) = (uint8_t)0x33U;
	*((volatile uint8_t *)AXI_M1 + 4) = (uint8_t)0x44U;
	*((volatile uint32_t *)AXI_M1) = 0xdeadbeefU;
	//(void)*((volatile uint32_t *)AXI_M1);

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
    }
	//	(void)*AXI_M1;
}

void irqCallback(){
}
