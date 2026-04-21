import fetch from "node-fetch";
import { onCall } from "firebase-functions/v2/https";

const GEMINI_API_KEY = "AIzaSyCMLIFMuSqmUXzruMkZoVxrwVMFtCYsZWs"; // 🔥 paste key here
const GEMINI_MODEL = "gemini-2.5-flash";

export const getPriorityScore = onCall(async (request) => {
  try {
    const { title, description } = request.data;

    if (!title || !description) {
      console.error("Missing title or description", request.data);
      throw new Error("Invalid request data");
    }

    const prompt = `
Given the following civic issue report:
Title: ${title}
Description: ${description}

Rate its urgency on a scale from 0.0 (not urgent) to 1.0 (most urgent).
Respond ONLY with a number between 0.0 and 1.0.
`;

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }]
        })
      }
    );

    const json = await response.json();
    console.log("Gemini response:", JSON.stringify(json, null, 2));

    const replyText = json?.candidates?.[0]?.content?.parts?.[0]?.text?.trim() ?? "0.5";
    const cleaned = replyText.replace(/[^0-9.]/g, ""); // handle "Priority: 0.8"
    const score = parseFloat(cleaned);
    const priority = isNaN(score) ? 0.5 : Math.min(Math.max(score, 0.0), 1.0);

    console.log(`→ Priority Score: ${priority}`);
    return { priority };
  } catch (err) {
    console.error("Gemini error:", err);
    return { priority: 0.5 };
  }
});
