require("dotenv").config();
const express = require("express");
const cors = require("cors");
const { GoogleGenAI, Type } = require("@google/genai");

const app = express();
app.use(cors());
app.use(express.json());

// Initialize Gemini Client using API Key from .env
const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
const PORT = process.env.PORT || 3000;

// Base Route
app.get("/", (req, res) => {
    res.json({ message: "Pantry Minder Backend is running" });
});

// Voice Parsing Endpoint
app.post("/api/voice/parse", async (req, res) => {
    try {
        const { text } = req.body;

        if (!text) {
            return res.status(400).json({ error: "Text prompt is required" });
        }

        // Pass today's date for relative expiry calculation (e.g., "in 2 months")
        const today = new Date().toISOString().split("T")[0];

        const systemInstruction = `
You are the Pantry Minder voice assistant.
Your job is to extract food item information from natural language.

Extract:
1. item name
2. quantity
3. unit
4. category
5. expiry date

Preferred Categories:
[Grains & Cereals, Vegetables, Fruits, Dairy Products, Meat & Seafood, Spices & Condiments, Beverages, Snacks & Sweets, Canned & Frozen Foods, Bakery Items, Oil & Fats, Cleaning Supplies, Others / Misc.]

Preferred Units:
[Kilogram (kg), Gram (g), Liter (L), Milliliter (ml), Piece(s), Packet, Bottle, Can, Box, Cup, Bunch, Dozen, Teaspoon (tsp), Tablespoon (tbsp), Jar, Bag, Loaf, Set]

Rules:
1. Today's date is ${today}. Calculate relative dates based on this.
2. If the user's category matches one in the preferred list, use that exact string. Otherwise, return the user's custom category string.
3. If the user's unit matches one in the preferred list, use that exact string. Otherwise, return the user's custom unit string.
4. Default quantity to 1 if unspecified.
5. Format expiryDate as YYYY-MM-DD or null if missing.
`;

        const response = await ai.models.generateContent({
            model: "gemini-3.5-flash",
            contents: text,
            config: {
                systemInstruction: systemInstruction,
                responseMimeType: "application/json",
                responseSchema: {
                    type: Type.OBJECT,
                    properties: {
                        name: { type: Type.STRING },
                        quantity: { type: Type.NUMBER },
                        unit: { type: Type.STRING },
                        category: { type: Type.STRING },
                        expiryDate: { type: Type.STRING, nullable: true }
                    },
                    required: ["name", "quantity", "unit", "category"]
                }
            }
        });

        const parsedData = JSON.parse(response.text);
        return res.json({ success: true, data: parsedData });

    } catch (error) {
        console.error("Gemini API Error:", error);
        return res.status(500).json({
            error: "Failed to parse text with Gemini",
            details: error.message
        });
    }
});

app.listen(PORT, () => {
    console.log(`Server running on http://localhost:${PORT}`);
});