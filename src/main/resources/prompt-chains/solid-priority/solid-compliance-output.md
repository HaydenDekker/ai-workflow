## Output format requirements

First, list each principle and assessment of the code against the principle before outputing a final answer.
        For each answer use some common sense if the principle application is too much of an edge case.
        The final answer must be a "YES" or "NO" followed by a json array following these rules:
        If the answer is NO then output an empty json array,
        
```json
         []
```

If the answer is YES, it does break a principle, then output an array of analysis objects where each object contains the following:

```
        {
          "principle": String,
          "issue": String,
          "fix": String
        }
```
