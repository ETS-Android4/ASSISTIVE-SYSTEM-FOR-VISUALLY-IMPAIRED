#include<SoftwareSerial.h>
SoftwareSerial BTserial(0,1);
const int pingTrigPin = 6; //Trigger connected to PIN D6  
const int pingEchoPin = 7; //Echo connected yo PIN D7
  
  int buz=5; //Buzzer to PIN 4   
  void setup() {   
  Serial.begin(9600); 
  BTserial.begin(9600);  
  pinMode(buz, OUTPUT);   
  }   
  void loop()   
  {   
  long duration, cm;   
  pinMode(pingTrigPin, OUTPUT);   
  digitalWrite(pingTrigPin, LOW);   
  delayMicroseconds(2);   
  digitalWrite(pingTrigPin, HIGH);   
  delayMicroseconds(5);   
  digitalWrite(pingTrigPin, LOW);   
  pinMode(pingEchoPin, INPUT);   
  duration = pulseIn(pingEchoPin, HIGH);   
  cm = microsecondsToCentimeters(duration); 
  
  if(cm<=320 && cm>0)   
  {   
  int d= map(cm, 1, 100, 20, 2000);   
  digitalWrite(buz, HIGH);   
  delay(100);   
  digitalWrite(buz, LOW);   
  delay(d);  
  }
  if(cm<=50){
      Serial.print(cm);    
      Serial.println("cm"); 
      BTserial.print(cm);
  }
  else{
    Serial.print(cm/100);    
    Serial.println("m"); 
    BTserial.print(cm/100);
  }
  
  delay(1000);   
  }   
  long microsecondsToCentimeters(long microseconds)   
  {   
  return microseconds / 29 / 2;   
  }   
