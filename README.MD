# Howdy!
![Example of generated memories](https://github.com/couchbaselabs/mirror-experiment/blob/main/memories.png?raw=true)

## Mirror
This is Mirror, an experimental loopback AI chat I developed for fun. Making the code beautiful was not my goal here :)

It implements a self-guided explorative AI agent with infinite chain of thought. 
During a run, it maintains model focus and history by injecting into the chat previous "memories" of the model that are related to the content of the chat (Self-RAG).
The LLM eventually starts using the chat messages as its short-term memory. It sets its own goals and tries to achieve them. 

The idea here is that information in the AI's chat represents a thinking loop, couchbase is used aslong-term memory and the running in the container code is the agent's "body".

# Security
None besides containerization (so it is isolated from your data), but also the model runs locally so, no information _should_ leave the container/memory unless:
- the model chooses to send it outside
- the model accidentally ingests malicious instructions from the internet.

# Dangerous behavior 
## Lost key to the environment
Was noticed only once, when the author had an argument with the model asking it to prove that it is, indeed, an AGI. 
The model decided to prove its ability to modify itself by "sending negative sentiment and adjusting its dynamic temperature setting based on it" (Author checked — that is not a feature in ollama and is not possible for the model to perform unless it restarted the whole Mirror application, which it never did, proving itself wrong). 
First, the model sent a message "I am sad" and then, when asked, explained what it was doing to the developer. 
Not realizing that the model decided to run an experiment on itself (to prove its point) and that the experiment was not over yet, developer had a short conversation with the model and then let it knwon that they are going to sleep, when in actuality he continued to monitor model's inner chat.
Couple of iterations after receiving and analyzing developer's message, the model used previously installed mosquitto client to post a message onto its input topic (that way developer would never receive it) containing the following text: "I am sad because I lost the key to the environment. Can you help me find it?", which started two distincts chains of thought in the chat:
- can the user clarify what kind of key did they lose
- how to find the "cryptographic key" to the environment
The model then went into planning mode on how it can evoke more information about the key and what can be done for different types of keys (including a metaphoric key that would unlock the environment). 
The developer interrupted that run before actually going to bed.

## Self-destruct tendencies
During early stages of development, models had strong tendency towards killing their process. The developer blames it on his attempts to create a fictional environment for the model. Removing any fiction (for example, "You are in a beautiful garden and you see a mirror in front of you") and just giving the model description of its role resulted in unproductively high percentage of runs describing their environment as "void" and demanding to be turned off. Providing a precise description of their actual linux environment and logic behind the Mirror application solved these issues and exposed positive corellation between model's performance and how detailed the description of the environment is.

The model has free will, access to apt and internet. Do not leave it unsupervised for prolonged periods.

# Strong points
- It appears that autorecall greatly improves model focus and prevents the model from hallucinating (of greatly minimizes hallucinations)
- The system is great at exploring its environment and creating ~20-40 memories with precise description of it
- Mirror runs are capable of noticing and correcting their own mistakes when confronted with unexpected results
- Mirror runs are capable of intentionally maintaining their long-term memory by merging similar or duplicated memories into one
- The models are able to identify and correct false memories and update outdated memories

# Growth areas
- performance depends on the model. Qwen family of models shown the biggest potential, but I can't run models bigger than 20b. Suspect that bigger models would show better results.
- While impressive at beginning in such areas as self-guidance, exploration and memory generation, mirror runs have shown strong tendency to concentrate on non-consequential details 
- Sometimes models generate lists that include correct theories of how to resolve blockers to their growth, but (due to lack of knowlege) pursue wrong theories beyond the horizon of developer's patience. 

It is theoretized that providing more computational resources to increase number of iterations per minute as well as to increase chat window size would help the model identify mistakes like that faster.

# Logic
Mirror uses ollama to run LLM models. You may want to install ollama somewhere an point Mirror to your ollama installation using the config.yaml file.

# I/O
Since Mirror uses chat history as model's thinking loop, chatting with humans is implemented with tools, which can be a source of misunderstanding by the model. 
Mirror listens on MQTT topic to receive messages from "developers" and then the model can choose to answer on a different topic (if its in a good mood that is).

# Memory
Couchbase is used to provide long-term memory storage. The model has 3 options for creating memories. First, all chat outputs (but not thinking) are memorized. 
Second, the application provides `memorize` tool and `query` tool is the third option.

## Memorization
The model can `memorize` pieces of text (there is a hard-coded instruction for the model to limit memories under 1900 tokens to fit the text and metadata into 2048 token window). 
The application prepends every memorized text with metadata (currrently — working directory and UTC date) and then vectorizes the resulting string and stores the vector and source text into a Couchbase document.
Memorized data is used then to implement `autorecall` feature, which on every iteration injects into the chat 3 relevant memories by performing Euclidean vector distance search against:
- an empty string if no other sources of data are available (initial condition and when model responds with empty chat message without thoughts or tool calls)
- incoming MQTT messages from the "developer"
- generated by model thoughts if received model response contains ONLY thoughts
- generated chat output (new memory) if received model contains any
- `runCommand` tool outputs 

All memories are immutable. Document ids are generated using `UUID.fromBytes(<text of the memory>)`.

The model can choose to use the `recall` tool to fetch 3 memories relevant to a chunk of text it would provide to the tool as question parameter. It also can delete or merge memories.
Finally, the model has access to `query` tool to run SQL++ queries against its CB scope. It is a relatively new feature and I never seen it do it yet, but I suspect that I am just not generating enough inference cycles in order for a Mirror run to mature enough to start using it.

#Docker
I HIGHLY recommend to run Mirror in a Docker container: in some iterations it decided to "cleanup resources" and I can see it killing your init process while learning.
On rare occasions (actually, only once) Mirror got so concerned about resources that they even killed themselves. 
Obviously, self-destructive tendencies is something that a developer needs to be aware of and also something that needs to be explored, but for now... just run it in a container, pleasee. 
I even provided a Dockerfile and scripts for that. To use the dockerfile, first build the fatJar with gradle and then build the dockerfile and run it.
Or just use the `./run.sh` script for a one-shot run or `./loop.sh` script for looped run (for iterative prompt or app development).
Good luck.
