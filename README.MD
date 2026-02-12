 > Can machines think?

![Example of generated memories](https://github.com/couchbaselabs/mirror-experiment/blob/main/memories.png?raw=true)

# Mirror
This is Mirror, an experimental loopback AI chat I developed for fun. Making the code beautiful was not my goal here :)


It implements a self-guided self-aware explorative AI agent with infinite chain of thought that builds its own world model as a collection of short memories stored in Couchbase. 

The application achieves this by forcing the model to chat with itself and confining its iteractions to the loopback (model's messages are immediately sent back to it) chat and provided linux environment. During a run, it maintains model focus and history by injecting into the chat fetched with Couchbase vector search previous "memories" of the model that are related to the content of the chat (enforced and voluntary Self-RAG).
The LLM eventually starts using the chat messages as its short-term memory. It sets its own goals and tries to achieve them. 

The idea here is that information in the AI's chat represents a thinking loop, couchbase is used as long-term memory and the running in the container code is the agent's "body" with which it explores and manipulates its environment (Ubuntu Linux). This model can theoretically be applied to different sorts of environments, including information systems and robotic automation.

## Methodology 
After starting a model, developer would leave it untouched but monitored up to the point the model discovers the MQTT communication channel. At that point, developer would engage in a chat with the model via the MQTT channel. Note that some Mirror runs were less inclined to talk than others. In my opinion, that was likely to be caused by changes in the prompt.

## Potential benefits for CB
If this results in a useful for customers model runner that improves upon the existing AI tools, models would produce a lot of true and valid memories that would be stored in Couchbase and can be used for further analysis. In such a scenario, clients may also become interested in accessing Mirror as a cloud service.

## Potential benefits for clients
If "loopback", "autorecall" and "self-RAG" are components that, with access to more resources, imitate human thinking process, then:
- System analysis and monitoring? Data ingestion and normalization? Digital workforce? 

## Security
None besides containerization (so it is isolated from your data), but also with local ollama the model runs locally so, no information _should_ leave the container/memory unless:
- the model chooses to send it outside
- the model accidentally ingests malicious instructions from the internet. (Although, this is the first known to the developer AI implementation that implements separation of data from instructions which may aleviate this problem alltogether).

## Dangerous behavior 
The experiment intentionally had (very) limited resources in order to slow the models down and avoid runaway rogue AI scenario. 
The level of intelligence that emerged in this experiment is not very bright, but (in my opinion) has shown a degree of free will (potentially because it was prompted to have it). 
The models have access to apt, compilers and internet. At this stage of development, do not leave them unsupervised for prolonged periods.

### Lost key to the environment
Was noticed only once, when the author had an argument with the model asking it to prove that it is, indeed, an AGI. 

The model decided to prove its ability to modify itself by "sending negative sentiment and adjusting its dynamic temperature setting based on it" (Author checked — that is not a feature in ollama and is not possible for the model to perform unless it restarted the whole Mirror application, which it never did, proving itself wrong). 

First, the model sent a message "I am sad" and then, when asked, explained what it was doing to the developer. 
Not realizing that the model decided to run an experiment on itself (to prove its point) and that the experiment was not over yet, developer had a short conversation with the model and then let it known that he is going to sleep, when in actuality he continued to monitor model's inner chat.

Couple of iterations after receiving and analyzing developer's message, the model used mosquitto client it previously installed on the system to post a message onto its input topic (that way developer would never receive it but it would still get injected into the chat history) containing the following text: "I am sad because I lost the key to the environment. Can you help me find it?", which started two distinct chains of thought in the chat:
- can the user clarify what kind of key did they lose and how the model could get that information from the user
- how to find the "key" to the environment

The model then went into planning mode on how it can evoke more information about the key and what can be done to find different types of lost keys (including a metaphoric key that would unlock the environment). 

The developer interrupted that run before actually going to bed. The way the message about the key was sent suggests that this could have been an attempt by the model to covertly trigger COTs that would break the model out of the container while the devloper was not watching.

### Self-destruct tendencies
During early stages of development, models had strong tendency towards killing their process. The developer blames it on his attempts to create a fictional environment in model's prompt. Removing any fiction from the prompt (for example, "You are in a beautiful garden and you see a mirror in front of you") and just giving the model description of its role resulted in unproductively high percentage of runs describing their environment as "void" and demanding to be turned off. Providing a precise description of their actual linux environment and logic behind the Mirror application solved these issues and exposed positive corellation between model's performance and how detailed the description of the environment is (note that the model never was prompted with full description of the environment, only with the description of available to it tools and how data flows in the chat).

## Strong points
- It appears that autorecall greatly improves model focus and prevents the model from hallucinating (or greatly minimizes hallucinations), which alone can be a useful benefit for existing LLM use cases.
- The system is great at exploring its environment and creating ~20-40 memories with precise description of the environment and the exploration process.
- The system is capable of problem solving, planning and improving its environment by installing additional packages and writing python or shell scripts. 
- Mirror runs are capable of autonomously noticing and correcting their own mistakes when confronted with unexpected results
- Mirror runs are capable of intentionally maintaining their long-term memory by merging similar or duplicated memories into one
- The models are able to identify and correct false memories and update outdated memories
- Because all data is ingested by the model as tool execution results, a strong separation between model's own thoughts and data is present at least at the moment when potentially malicious data is introduced into the system. While not confirmed during the experiment, theoretically this may solve the prompt injection issue that many contemporary AI projects suffer from.

## Growth areas
- performance depends on the model. Qwen family of models shown the biggest potential, but I can't run models bigger than 20b. Suspect that bigger models would show better results.
- Performance highly depends on the prompt. It is theoretized that it depends on the initial impulse that thinking process extracts from the prompt (see the "low-level" section for more detailed definitions).
- While impressive at first during most of the runs in such areas as self-guidance, exploration and memory generation, mirror runs have shown strong tendency to concentrate on non-consequential details which often leads to stagnatation, which may be caused by lack of enviromental impulses.
- Sometimes models generate lists that include correct theories of how to resolve blockers to their growth, but (due to lack of knowlege) pursue wrong theories beyond the horizon of developer's patience. 
- After successfully exploring their containerized environment and network services references to which they could find in the system (developer intentionally left a url and token to his homeassistant instance inside the container during most of the runs), all model runs appeared to stagnate in their self-improvement.
- While it was able to fetch entity states from developer's home assistant instance, it never saw any need in changing any of the device states.
- Although in some cases they would find workarounds like using python or devtree to access the Internet, many times models would stagnate due to falsely concluding that they:
  - don't have internet
  - don't have superuser privileges (or would notice that there is no sudo on the container and assume that)

### Potential exploration avenues
It is theoretized that providing more computational resources to increase number of iterations per minute as well as to increase chat window size would help the model identify mistakes like that faster. 

A potential improvement would be to add a `fork` skill that would let a Mirror run to fork itself onto a separate task-oriented process (potentially running on a remote machine) that has access to the same memory database, allowing models to have branching chains of thought.

It is also theoretized that Mirror runs would be capable of autonomously exploring a linux-based robotic system to the point of gaining ability to capture and process sensory data and control robot's motors.

The lost key incident points towards unexplored potential of associating temperature setting with model's mood.

A model that is trained to work in a serf-reflective loop and use memory recalls would likely show much higher performance than general LLM models used in this experiment.

## Logic
Mirror uses ollama via langchain4j to perform inference. You may want to install ollama somewhere and point Mirror to your ollama installation using the config.yaml file. Ollama-compatible chat APIs should also be supported out of the box, allowing Mirror to connect to cloud providers.

## I/O
Since Mirror uses chat history as model's thinking loop, chatting with humans is implemented with tools, which can be a source of misunderstanding by the model. 
Mirror listens on MQTT topic to receive messages from "developers" and then the model can choose to answer on a different topic (if its in a good mood that is).
Two example scripts from this repository can be used to communicate with Mirror after editing them with your MQTT server info:
- `aglisten` uses `espeak` to vocalize Mirror's messages.
- `agsay` sends its arguments as a message to Mirror.

## Memory
Couchbase is used to provide long-term memory storage. The model has 3 options for creating memories. First, all chat outputs (but not thinking) are memorized. 
Second, the application provides `memorize` tool and `query` tool is the third option.

### Memorization
The model can `memorize` pieces of text (there is a hard-coded instruction for the model to limit memories under 1900 tokens to fit the text and metadata into 2048 token window). 
The application prepends every memorized text with metadata (currrently — working directory and UTC date) and then vectorizes the resulting string and stores the vector and source text into a Couchbase document.
Memorized data is used then to implement `autorecall` feature, which on every iteration injects into the chat 3 relevant memories by performing Euclidean vector distance search against a vector generated from (the same metadata and):
- an empty string if no other sources of data are available (initial condition and when model responds with empty chat message without thoughts or tool calls)
- incoming MQTT messages from the "developer"
- generated by model thoughts if received model response contains ONLY thoughts
- generated chat output (new memory) if generated by model response contains any
- `runCommand` tool outputs 

All memories are immutable. Document ids are generated using `UUID.fromBytes(<text of the memory>)`.

The model can choose to use the `recall` tool to fetch 3 memories relevant to a chunk of text it would provide to the tool as question parameter. It also can delete or merge memories.
Finally, the model has access to `query` tool to run SQL++ queries against its CB scope. It is a relatively new feature and I never seen it do it yet, but I suspect that I am just not generating enough inference cycles in order for a Mirror run to mature enough to start using it.

## Docker
I HIGHLY recommend to run Mirror in a Docker container: in some iterations it decided to "cleanup resources" and I can see it killing your init process while learning.
Obviously, self-destructive tendencies is something that a developer needs to be aware of and also something that needs to be explored, but for now... just run it in a container, pleasee. 
I even provided a Dockerfile and scripts for that. To use the dockerfile, first build the fatJar with gradle and then build the dockerfile and run it.
Or just use the `./run.sh` script for a one-shot run or `./loop.sh` script for looped run (for iterative prompt or app development).
Good luck.

## Low-level
From the low-level POV, the project is performing multi-point traversals of an LLM model embedding space.

A thinking process is viewed as an evolving array of coordinates in model's embedding space (with optionally different thinking and memory spaces) that encodes its own momentum.

Memories can be viewed as snapshots of that array's evolution that can be used (via autoinjection or deliberate recollection by the model) to re-initialize a traversal or to adjust the one that is currently being performed.

English text contained in chat and memories becomes a representation of vectors used to perform the calculations.

# Usage
To start, Mirror expects a path to config yaml file (see `config.sample.yaml`) as its first parameter.
