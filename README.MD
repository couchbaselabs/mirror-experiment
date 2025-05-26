Howdy!

This is Loopus, a microservice that runs an LLM model, looping its outputs to its inputs and providing input/output interface to the model.

I/O
IO is implemented via mqtt: the model can "think" to "say" something and it will be published onto a pre-configured mqtt output topic. 
Loopus would also inject into the "thinking loop" any message that it reads from a pre-configured mqtt input topic.