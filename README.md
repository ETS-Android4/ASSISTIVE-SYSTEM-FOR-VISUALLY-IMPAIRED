# ASSISTIVE-VISION-FOR-VISUALLY-IMPAIRED

***FILES IN PROJECT***      
**a.Files containing code**   
android: This file contains the code for  android application installed in person's smart phone.      
raspberry_pi: This File contains code for raspberry_pi  which will be installed on the gloves of persons hands.    
Third_eye_for_blinds.ino : This File contains the code for Arduino.    

**b.Files to help visualise the project**    
PPPT.ppts: This file contains a ppt representation of the project.    
TESTINGVIDEO.mp4:This file contains a **video of the working project**.Please download this file to see the project in action.    
  
    
  
***ABOUT PROJECT***    
Developed a project during my final year of Engineering"Assistive Gloves for visually impaired"  
The basic idea of this project is, the use of an ultrasonic sensor along with a Smartphone to aid the a person suffering from being visually disabled.

The project was divided into 2 parts: Distance Measurement, Image recognition    
**1.Distance Measurement**   
The ultrasonic sensors were used for distance measurement and Arduino which was programmed on Arduino IDE and was used for the processing which vibrated the haptic feedback  
**2.Image recognition:**  
Tools and technologies used were person's smartphone, Android studio, Python IDE    
The implementation of this stage involves the development of a mobile application currently supported only by Android devices. The idea is that the user will carry his/her smartphone in a case that will be worn in the neck and the device’s rear camera will be facing the objects that are going to appear in front of the user. The role of the application is to utilize the primary (rear) camera to get images real-time, process them, and identify the object present in a frame. The advantage of doing object detection is that you get to recognize multiple objects at a time.  

**HERE IS A SAMPLE OUTPUT TABLE:**  
Object detected:       Laptop chair car  
Detection confidence(%): 75  56   72   
Distance measured(m):    -   -   3.3  
Inference Time(ms):     226  163 337  

