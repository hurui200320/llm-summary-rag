# llm-summary-rag

## Definition of the issue

I was trying to feed a bunch of books (more specifically, all 24 volumes of the Spice and Wolf series)
into a large language model (LLM). I would like to have it read all the content, then I can chat with it
to discuss various details in the story.

However, with normal RAG, the model can only get pieces of the original content based on your input prompt.
And overall, considering the span of the stories, normal RAG is not working well under this scenario.

I also tried agentic RAG, where the model can generate its own query and use it to fetch the relevant content.
However, agentic RAG relies on the base model's knowledge to generate such queries.

Shortly speaking:

> LLM's knowledge = info stored in weights + info provided in the context
> 
> The content in RAG is not in either of the two categories, thus, they are totally unknown to the LLM.
> Just like any other human, you can't ask about things you don't know you don't know.

This project is a side project for myself to get some fun, by trying to solve this issue on Java.

I know there are a bunch of possible solutions on Python, for example, GraphRAG from Microsoft,
or even Python's langchain library has some built-in solution to ease the issue.
But no, thanks. I don't like Python.

## Organization of the trials

Unlike my other projects, this project is more like a test field.
Thus, each branch represents a different trial.
The master branch is the most promising one which I'm continuing moving forward.
All other branches are just failed attempts.