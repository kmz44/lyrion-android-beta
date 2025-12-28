/*
 * ========================================
 * TEST DE DIAGNÓSTICO - TB6612FNG DRIVER
 * ========================================
 * 
 * Placa: Ingeniero Maker con Arduino Nano
 * Driver: TB6612FNG (1.2A nominal, 3A pico max)
 * 
 * Este sketch prueba TODAS las conexiones
 * para encontrar problemas en el hardware
 * 
 * INSTRUCCIONES:
 * 1. Sube este código al Arduino
 * 2. Abre el Serial Monitor (9600 baudios)
 * 3. Observa qué motores se mueven
 * 4. Anota cualquier comportamiento extraño
 * 
 * PINES CORRECTOS SEGÚN DATASHEET:
 * Motor A: PWMA=D5, AIN1=D4, AIN2=D9
 * Motor B: PWMB=D6, BIN1=D7, BIN2=D8
 */

// --- PINES TB6612FNG (CORRECTOS) ---
// Motor A
#define PWMA 5   // D5 - PWM velocidad Motor A
#define AIN1 4   // D4 - Dirección 1 Motor A
#define AIN2 9   // D9 - Dirección 2 Motor A

// Motor B
#define PWMB 6   // D6 - PWM velocidad Motor B
#define BIN1 7   // D7 - Dirección 1 Motor B
#define BIN2 8   // D8 - Dirección 2 Motor B

void setup() {
  Serial.begin(9600);
  
  // Esperar conexión serial
  delay(2000);
  
  Serial.println("\n\n");
  Serial.println("========================================");
  Serial.println("  TEST DE DIAGNÓSTICO - DRIVER MOTORES");
  Serial.println("========================================");
  Serial.println();
  
  // Configurar todos los pines
  pinMode(AIN1, OUTPUT);
  pinMode(AIN2, OUTPUT);
  pinMode(PWMA, OUTPUT);
  pinMode(BIN1, OUTPUT);
  pinMode(BIN2, OUTPUT);
  pinMode(PWMB, OUTPUT);
  
  // Asegurar que todo esté apagado
  apagarTodo();
  
  Serial.println("Configuración de pines:");
  Serial.print("  Motor A: IN1="); Serial.print(AIN1);
  Serial.print(", IN2="); Serial.print(AIN2);
  Serial.print(", PWM="); Serial.println(PWMA);
  
  Serial.print("  Motor B: IN1="); Serial.print(BIN1);
  Serial.print(", IN2="); Serial.print(BIN2);
  Serial.print(", PWM="); Serial.println(PWMB);
  Serial.println();
  
  Serial.println("⚠️  IMPORTANTE:");
  Serial.println("   - Verifica que el driver tenga alimentación externa");
  Serial.println("   - El LED del driver debe estar encendido");
  Serial.println("   - Baterías/fuente conectada al driver (no al Arduino)");
  Serial.println();
  
  delay(3000);
  
  // Iniciar secuencia de pruebas
  Serial.println("🚀 Iniciando pruebas en 2 segundos...");
  delay(2000);
  
  ejecutarPruebas();
  
  Serial.println();
  Serial.println("========================================");
  Serial.println("  PRUEBAS COMPLETADAS");
  Serial.println("========================================");
  Serial.println();
  Serial.println("📝 ¿Qué observaste?");
  Serial.println("   A) Ningún motor se movió → Problema de alimentación");
  Serial.println("   B) Solo 1 motor se movió → Revisar cables del otro");
  Serial.println("   C) Ambos se movieron → ¡Perfecto! Sube el código principal");
  Serial.println("   D) Se mueven al revés → Normal, se corrige en el código");
  Serial.println();
}

void loop() {
  // En loop no hacemos nada, todo está en setup()
  // Puedes reiniciar el Arduino (botón RESET) para volver a ejecutar
  delay(1000);
}

// ========================================
// FUNCIONES DE PRUEBA
// ========================================

void ejecutarPruebas() {
  
  // TEST 1: Verificar pines PWM
  Serial.println("\n--- TEST 1: Verificación de pines PWM ---");
  Serial.println("Encendiendo PWM al 100%...");
  analogWrite(PWMA, 255);
  analogWrite(PWMB, 255);
  delay(500);
  Serial.println("✓ Pines PWM configurados");
  analogWrite(PWMA, 0);
  analogWrite(PWMB, 0);
  delay(1000);
  
  // TEST 2: Motor A - Dirección 1
  Serial.println("\n--- TEST 2: Motor A (Izquierdo) ---");
  Serial.println("→ Dirección 1 (IN1=HIGH, IN2=LOW)");
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 200);  // Velocidad media
  
  Serial.println("   Observa: ¿El motor A gira?");
  Serial.println("   Esperando 3 segundos...");
  delay(3000);
  
  // TEST 3: Motor A - Dirección 2
  Serial.println("→ Dirección 2 (IN1=LOW, IN2=HIGH)");
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, 200);
  
  Serial.println("   Observa: ¿El motor A gira al revés?");
  Serial.println("   Esperando 3 segundos...");
  delay(3000);
  
  // Apagar Motor A
  apagarMotorA();
  Serial.println("✓ Motor A detenido");
  delay(1000);
  
  // TEST 4: Motor B - Dirección 1
  Serial.println("\n--- TEST 3: Motor B (Derecho) ---");
  Serial.println("→ Dirección 1 (IN1=HIGH, IN2=LOW)");
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 200);
  
  Serial.println("   Observa: ¿El motor B gira?");
  Serial.println("   Esperando 3 segundos...");
  delay(3000);
  
  // TEST 5: Motor B - Dirección 2
  Serial.println("→ Dirección 2 (IN1=LOW, IN2=HIGH)");
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, 200);
  
  Serial.println("   Observa: ¿El motor B gira al revés?");
  Serial.println("   Esperando 3 segundos...");
  delay(3000);
  
  // Apagar Motor B
  apagarMotorB();
  Serial.println("✓ Motor B detenido");
  delay(1000);
  
  // TEST 6: Ambos motores - Misma dirección
  Serial.println("\n--- TEST 4: AMBOS motores simultáneos ---");
  Serial.println("→ Ambos en dirección 1");
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 200);
  
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 200);
  
  Serial.println("   Observa: ¿Ambos giran juntos?");
  Serial.println("   Esperando 3 segundos...");
  delay(3000);
  
  // TEST 7: Ambos motores - Dirección opuesta
  Serial.println("→ Ambos en dirección 2");
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, HIGH);
  analogWrite(PWMA, 200);
  
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, HIGH);
  analogWrite(PWMB, 200);
  
  Serial.println("   Observa: ¿Ambos giran al revés?");
  Serial.println("   Esperando 3 segundos...");
  delay(3000);
  
  // TEST 8: Prueba de velocidades
  Serial.println("\n--- TEST 5: Diferentes velocidades ---");
  Serial.println("→ Velocidad BAJA (PWM=100)");
  digitalWrite(AIN1, HIGH);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 100);
  
  digitalWrite(BIN1, HIGH);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 100);
  delay(2000);
  
  Serial.println("→ Velocidad MEDIA (PWM=180)");
  analogWrite(PWMA, 180);
  analogWrite(PWMB, 180);
  delay(2000);
  
  Serial.println("→ Velocidad ALTA (PWM=255)");
  analogWrite(PWMA, 255);
  analogWrite(PWMB, 255);
  delay(2000);
  
  // Apagar todo
  apagarTodo();
  Serial.println("✓ Todos los motores detenidos");
  delay(1000);
}

// ========================================
// FUNCIONES AUXILIARES
// ========================================

void apagarTodo() {
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 0);
  
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 0);
}

void apagarMotorA() {
  digitalWrite(AIN1, LOW);
  digitalWrite(AIN2, LOW);
  analogWrite(PWMA, 0);
}

void apagarMotorB() {
  digitalWrite(BIN1, LOW);
  digitalWrite(BIN2, LOW);
  analogWrite(PWMB, 0);
}
