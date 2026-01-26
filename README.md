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

## Trial: text splitting by LLM

This trial tried to split the content into chunks by LLM.
Basically, it asks the LLM to decide where is the good place to cut the text within the limit of the chunk size.

However, asking LLM to output the whole chunk is basically asking it to repeat the whole book.
So it's not a good idea. Instead, we ask it to only output the tail, and we search for the index and make the cut.

Issues:

+ When testing with *Bloom Into You: Regarding Saeki Sayaka*, GCP's API blocked content and didn't tell me why.
  + The OpenRouter API doesn't, but the gemini-3-falsh model feels like a idiot and always failed to follow the system instruction
  + The gemini-3-pro over OpenRouter is fine, but it's too slow with low reasoning effort and is too expensive.

Need to find a cheaper way to do that. Gemini has a batch API, which takes at most 24 hour to process,
but the price is 50% of the normal price.

However, the current process relies on previous output, so it doesn't really work for batch.

Maybe we can do hard cut slicing but skip the chunking part and directly ask AI to summarize?

For example, each chunk is about 250 tokens (use embedding token estimator to decide). Then:

`A | BC | D`, A and D are solely present for context, so AI can know what happens even if we cut
at the middle of the sentence. Then we ask AI to summarize the `BC` chunk, which is about 500 tokens in total.

When putting into RAG, we record those three parts in metadata, but only calculate `BC` for the vector.

Next turn we do: `C | DE | F`, ...

Initially we can do: ` (empty) | AB | C`, and at the end we do ` X | YZ | (empty)`.

In practice, we can use something like PEM:

```
-----BEGIN PREVIOUS BLOCK-----
Content of A here...
-----END PREVIOUS BLOCK-----
-----BEGIN CURRENT BLOCK-----
Content of B and C here...
-----END CURRENT BLOCK-----
-----BEGIN NEXT BLOCK-----
Content of D here...
-----END NEXT BLOCK-----
```

TODO: test how LLM behaves with this approach. If it works good, we can slice the text