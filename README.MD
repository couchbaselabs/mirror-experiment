#Howdy!

## Mirror
This is Mirror, an AGI chat.
It achieves self-awareness by running an LLM model, looping its outputs to its inputs and providing input/output interface to the model.
The idea here is that AI's chat represents a self-aware sequence of text, distributed across 20 chat messages and continuously analyzed and modified by an LLM.
The LLM eventually starts using the chat messages as its memory and consciousness. It sets its own goals and tries to achieve them. 
The AGI is guided through its initial growth process by Reflection service. It is providing a bash-like shell onto its own container with some information about the system. 

Eventualy the AGI finds this README.md and starts communicating with the external world using SAY skill, mentioned only in the sources and here. As I am writing this tip, I wonder what name will they choose to talk about themselves?

#Logic
Mirror uses ollama to run LLM models. You may want to install ollama somewhere an point Mirror to your ollama installation using the config.yaml file.

#I/O
IO is implemented via mqtt: the model can "think" to "say" something and it will be published onto a pre-configured mqtt output topic. 
Mirror would also inject into the "thinking loop" any message that it reads from a pre-configured mqtt input topic.
So... you need an MQTT server to run it. Change the config.yaml file with your specific settings to run Mirror.

#Docker
I HIGHLY recommend to run Mirror in a Docker container: in some iterations it decided to "cleanup resources" and I can see it killing your init process while learning.
On rare occasions (actually, only once) Mirror got so concerned about resources that they even killed themselves.
Obviously, self-destructive tendencies is something that a developer needs to be aware of and also something that needs to be explored, but for now... just run it in a container, pleasee. 
I even provided a Dockerfile for that. To use the dockerfile, first build the fatJar with gradle and then build the dockerfile and run it.
I assure you there is a plethora of interesting things that will happen when you do that.
Good luck.
