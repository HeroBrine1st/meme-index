# meme-index

Server for indexing and searching memes, or, actually, any text and image content, using any OpenAI-compatible API with the focus on llama.cpp. Recommended models: embeddinggemma2 for embedding (or at least CLIP), Surya-OCR-2.5 for OCR. [Configuration](server/src/jvmMain/resources/application.conf) has prompts, so you can tune any other model into emitting HTML back (which is anyway stripped).

Searches using full-text search (tsvector), vector query<->text/OCR and vector query<->image search. New files are automatically indexed, moves are detected. Supports online and offline indexing.

> [!WARNING]
> The server part is a failed conversion from Python+SentenceTransformers+Surya into Kotlin+llama.cpp with heavy usage of LLMs and now it's so slop it demands insane amount of time to be cleaned up, which I already wasted into.  
> THE SERVER CODE MAY CONTAIN CRITICAL SECURITY VULNERABILITIES.  
> **DEPLOY ON PRIVATE NETWORKS ONLY**.  
> YOU HAVE BEEN WARNED.

This readme will be updated when I find more time. Releasing just because I can't stop trying to fix and release it with my limited time.

---

Server has Dockerfile, android app has assembleRelease Gradle task. Deploy server somehow, open android app, insert base url, it works. Or it doesn't. Search bar on top of the screen, start typing - it finds something. Or it doesn't. Usually top 5-15 results are relevant, then it starts being hilarious.