/*
 * TEST SIMPLE - TB6612FNG DRIVER
 * ================================
 * Para placa Ingeniero Maker con Arduino Nano
 * Driver: TB6612FNG (1.2A nominal, 3A pico)
 * 
 * SECUENCIA:
 * 1. LED parpadea 3 veces al inicio
 * 2. Motor A gira (3 seg adelante, 3 seg atrás)
 * 3. Motor B gira (3 seg adelante, 3 seg atrás)
 * 4. Ambos giran juntos
 * 5. Se repite indefinidamente
 * 
 * PINES CORRECTOS SEGÚN DATASHEET:
 * Motor A: PWMA=D5, AIN1=D4, AIN2=D9
 * Motor B: PWMB=D6, BIN1=D7, BIN2=D8
 */

// --- Pines Motor A (TB6612FNG) ---
#define PWMA 5   // PWM velocidad Motor A
#define AIN1 4   // Dirección Motor A
#define AIN2 9   // Dirección Motor A

// --- Pines Motor B (TB6612FNG) ---
#define PWMB 6   // PWM velocidad Motor B
#define BIN1 7   // Dirección Motor B
#define BIN2 8   // Dirección Motor B

#define LED_PIN 13  // LED integrado del Arduino

int velocidad = 200;  // Velocidad de prueba (0-255)

void setup() {
  pinMode(AIN1, OUTPUT);
  pinMode(AIN2, OUTPUT);
  pinMode(PWMA, OUTPUT);
  pinMode(BIN1, OUTPUT);
  pinMode(BIN2, OUTPUT);
  pinMode(PWMB, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  
  // Indicador visual de inicio
  for(int i=0; i<3; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(300);
    digitalWrite(LED_PIN, LOW);
    delay(300);
  }
  
  delay(2000);  // Espera antes de empezar
}

void loop() {
  // LED encendido = Motor A
  digitalWrite(LED_PIN, HIGH);
  
  // Motor A adelante
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, velocidad);
  delay(3000);
  
  // Motor A atrás
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, velocidad);
  delay(3000);
  
  // Apagar A
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 0);
  digitalWrite(LED_PIN, LOW);
  delay(1000);
  
  // LED parpadeante = Motor B
  digitalWrite(LED_PIN, HIGH);
  delay(200);
  digitalWrite(LED_PIN, LOW);
  delay(200);
  digitalWrite(LED_PIN, HIGH);
  
  // Motor B adelante
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, velocidad);
  delay(3000);
  
  // Motor B atrás
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, velocidad);
  delay(3000);
  
  // Apagar B
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 0);
  digitalWrite(LED_PIN, LOW);
  delay(1000);
  
  // LED constante = Ambos motores
  digitalWrite(LED_PIN, HIGH);
  
  // Ambos adelante
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, velocidad);
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, velocidad);
  delay(3000);
  
  // Ambos atrás
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, velocidad);
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, velocidad);
  delay(3000);
  
  // Apagar todo
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 0);
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 0);
  digitalWrite(LED_PIN, LOW);
  
  delay(3000);  // Pausa antes de repetir
}
