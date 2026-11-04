import { NextResponse } from 'next/server';
import Anthropic from '@anthropic-ai/sdk';
import { getAdminUser } from '@/lib/supabase/auth';

export const runtime = 'nodejs';

const ALLOWED = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'] as const;

const SCAN_TOOL: Anthropic.Tool = {
  name: 'record_scoreboard',
  description: 'Record every player row read from the Valorant scoreboard screenshot.',
  strict: true,
  input_schema: {
    type: 'object',
    additionalProperties: false,
    required: ['players'],
    properties: {
      players: {
        type: 'array',
        description: 'One entry per player row, top to bottom.',
        items: {
          type: 'object',
          additionalProperties: false,
          required: [
            'name',
            'combat_score',
            'kills',
            'deaths',
            'assists',
            'econ',
            'first_bloods',
            'plants',
            'defuses',
          ],
          properties: {
            name: { type: 'string', description: 'Username exactly as printed' },
            combat_score: { type: 'integer' },
            kills: { type: 'integer' },
            deaths: { type: 'integer' },
            assists: { type: 'integer' },
            econ: { type: 'integer' },
            first_bloods: { type: 'integer' },
            plants: { type: 'integer' },
            defuses: { type: 'integer' },
          },
        },
      },
    },
  },
};

const INSTRUCTIONS = [
  'This is a Valorant scoreboard screenshot. Each player row reads left to right as:',
  'username  combatscore  kills/deaths/assists  econ  firstblood  plant  defuse',
  'Read every player row (usually 10). Use the exact username text, including tags and casing.',
  'combat_score is the ACS / combat score column. econ is the econ rating column.',
  'kills, deaths and assists come from the single "K / D / A" cell.',
  'first_bloods, plants and defuses are the last three numeric columns, in that order.',
  'If a value is blank or unreadable, use 0. Call record_scoreboard once with all rows.',
].join('\n');

export async function POST(req: Request) {
  if (!(await getAdminUser())) {
    return NextResponse.json({ error: 'Unauthorized.' }, { status: 401 });
  }
  if (!process.env.ANTHROPIC_API_KEY) {
    return NextResponse.json(
      { error: 'ANTHROPIC_API_KEY is not set on the server.' },
      { status: 500 },
    );
  }

  const form = await req.formData();
  const file = form.get('image');
  if (!(file instanceof File)) {
    return NextResponse.json({ error: 'No image uploaded.' }, { status: 400 });
  }
  const mediaType = file.type;
  if (!(ALLOWED as readonly string[]).includes(mediaType)) {
    return NextResponse.json(
      { error: `Unsupported image type: ${mediaType || 'unknown'}.` },
      { status: 400 },
    );
  }

  const data = Buffer.from(await file.arrayBuffer()).toString('base64');
  const client = new Anthropic();

  let message: Anthropic.Message;
  try {
    message = await client.messages.create({
      model: 'claude-opus-5',
      max_tokens: 4000,
      thinking: { type: 'disabled' },
      tools: [SCAN_TOOL],
      tool_choice: { type: 'tool', name: 'record_scoreboard' },
      messages: [
        {
          role: 'user',
          content: [
            {
              type: 'image',
              source: { type: 'base64', media_type: mediaType as 'image/png', data },
            },
            { type: 'text', text: INSTRUCTIONS },
          ],
        },
      ],
    });
  } catch (err) {
    const msg =
      err instanceof Anthropic.APIError
        ? `Claude API error ${err.status ?? ''}: ${err.message}`
        : 'Scan request failed.';
    return NextResponse.json({ error: msg }, { status: 502 });
  }

  const toolUse = message.content.find((b) => b.type === 'tool_use');
  if (!toolUse || toolUse.type !== 'tool_use') {
    return NextResponse.json({ error: 'Claude did not return any rows.' }, { status: 422 });
  }

  return NextResponse.json(toolUse.input);
}
