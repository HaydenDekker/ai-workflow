## Output format requirements

To determine the priority use the following table:

### SOLID Violation Severity Table

<table>
  <thead>
    <tr>
      <th>SOLID Violation Severity (Y-Axis)</th>
      <th>Core Logic Class (High Impact)</th>
      <th>Adapter/Service Class (Medium Impact)</th>
      <th>Utility/Helper Class (Low Impact)</th>
      <th>Special Cases</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>Rank 1: SRP/OCP</b></td>
      <td>Highest Priority (<b>P1</b>)</td>
      <td>High Priority (P2)</td>
      <td>Medium Priority (P3)</td>
      <td>Builder Pattern (<b>P5</b>)</td>
    </tr>
    <tr>
      <td><b>Rank 2: LSP/ISP</b></td>
      <td>High Priority (P2)</td>
      <td>Medium Priority (P3)</td>
      <td>Lower Priority (P4)</td>
      <td>Builder Pattern (<b>P5</b>)</td>
    </tr>
    <tr>
      <td><b>Rank 3: DIP</b></td>
      <td>Medium Priority (P3)</td>
      <td>Lower Priority (P4)</td>
      <td>Lowest Priority (<b>P5</b>)</td>
    </tr>
  </tbody>
</table>      
             
First explain your understanding of the table.

Secondly, assess the Special Cases and determine if a special case applies.

Finally, create the response in the format below,

Provide response in the following json format,
        
```json
  {
    priorityWeight: Number 1 .. 5
    priotyRationale: String,
    explainationOfRectification: String
  }
```

If there are no responses, respond with an empty list.
If there is only one response, make sure its wrapped in a list.