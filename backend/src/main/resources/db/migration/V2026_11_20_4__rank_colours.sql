-- Each Valorant tier's colour, for the rank pills: the import brought the ranks over without
-- any, so every pill showed plain text. Only ranks still without a colour are given one.
UPDATE ranks
SET color = CASE split_part(rank_name, ' ', 1)
  WHEN 'Iron' THEN '#6E6E6E'
  WHEN 'Bronze' THEN '#A5855D'
  WHEN 'Silver' THEN '#B4B9B9'
  WHEN 'Gold' THEN '#E5B842'
  WHEN 'Platinum' THEN '#3B9DB4'
  WHEN 'Diamond' THEN '#9367BE'
  WHEN 'Ascendant' THEN '#2E9B5E'
  WHEN 'Immortal' THEN '#B33E52'
  WHEN 'Radiant' THEN '#D6C07E'
END
WHERE color IS NULL
  AND split_part(rank_name, ' ', 1) IN
    ('Iron', 'Bronze', 'Silver', 'Gold', 'Platinum', 'Diamond', 'Ascendant', 'Immortal', 'Radiant');
