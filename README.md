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

## Trial: hard cut with context summarization

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

Test using Gemini 3 flash works, but a lot of content has been blocked due to PROHIBITED CONTENT. In total, 14 out of 101 chunks failed to summary.

Rollback to Gemini 2.5 flash. 9 out of 101 chunks failed to summary, but the quality of the summary is significantly worse.

Trying Gemini 3 pro，11 out of 101 chunks failed to summary. The quality has not improved much.

Tried GPT-5.1, which is all good. The GPT 5.2 improves tool calling and agentic tool,
which is not useful here, so just using 5.1 to save some money.

For RAG, since the raw texts are hard cut, so there is no point to do RAG.
However, we do want to do RAG for both chunk summary and book summary,
providing tools to search RAG and return both matched books and chunks.
Also providing search text using something like Apache Lucene.

Re rank? No, don't over optimize.

For Lucene:
  + SmartChineseAnalyser looks promissing, but can't handle simplified and traditional Chinese
  + Replace with ICU tokenizer with N gram tokenizer

Tools:
+ List docs (id, title, author, summary, lang)
+ List chunks (docId) - chunk index, chunk summary
+ Search doc summaries RAG - return matched docs id and their summary
+ Search chunk summaries RAG - return matched (docId, chunkIndex) and their summary
+ Search text - return matched (docId, chunkIndex)
+ Read chunk (docId, chunk Index)