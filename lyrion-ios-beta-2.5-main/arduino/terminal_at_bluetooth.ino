/*
 * =============================================
 * CONFIGURADOR AVANZADO - BLUETOOTH HC-05/HC-06
 * =============================================
 * 
 * Este código te permite configurar el módulo Bluetooth
 * de forma interactiva desde el Serial Monitor
 * 
 * MODO DE USO:
 * 1. Conecta el Bluetooth al Arduino
 * 2. Sube este código (con Bluetooth DESCONECTADO)
 * 3. Reconecta el Bluetooth
 * 4. Abre Serial Monitor (9600 baudios)
 * 5. Activa "Both NL & CR" en el Serial Monitor
 * 6. Envía comandos AT directamente
 * 
 * CONEXIÓN:
 * HC-05/HC-06  →  Arduino Nano
 * -----------     ------------
 * VCC         →   5V
 * GND         →   GND
 * TXD         →   RX (D0)
 * RXD         →   TX (D1)
 * =============================================
 */

void setup() {
  Serial.begin(9600);
  
  delay(1500);
  
  Serial.println("\n\n");
  Serial.println("========================================");
  Serial.println("  TERMINAL AT - BLUETOOTH HC-05/HC-06");
  Serial.println("========================================");
  Serial.println();
  Serial.println("✅ Terminal listo");
  Serial.println();
  Serial.println("📝 COMANDOS AT COMUNES:");
  Serial.println("   AT              → Test conexión");
  Serial.println("   AT+VERSION      → Ver versión");
  Serial.println("   AT+NAME=nombre  → Cambiar nombre");
  Serial.println("   AT+PIN=1234     → Cambiar PIN");
  Serial.println("   AT+BAUD4        → 9600 baudios");
  Serial.println("   AT+BAUD6        → 38400 baudios");
  Serial.println("   AT+BAUD7        → 57600 baudios");
  Serial.println("   AT+BAUD8        → 115200 baudios");
  Serial.println();
  Serial.println("💡 Para HC-05, algunos comandos necesitan '\\r\\n'");
  Serial.println("   Activa 'Both NL & CR' en el Serial Monitor");
  Serial.println();
  Serial.println("========================================");
  Serial.println("Escribe tus comandos abajo:");
  Serial.println();
}

void loop() {
  // Del Serial Monitor al Bluetooth
  if (Serial.available()) {
    char c = Serial.read();
    Serial.write(c);  // Echo para ver lo que escribes
  }
  
  // Del Bluetooth al Serial Monitor
  while (Serial.available()) {
    delay(10);
    char c = Serial.read();
    Serial.write(c);
  }
}
