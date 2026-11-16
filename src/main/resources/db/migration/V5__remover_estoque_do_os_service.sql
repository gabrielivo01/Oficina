ALTER TABLE item_ordem_servico ADD COLUMN nome_peca VARCHAR(150);
UPDATE item_ordem_servico
SET nome_peca = (SELECT nome FROM peca WHERE peca.id = item_ordem_servico.peca_id)
WHERE peca_id IS NOT NULL;
ALTER TABLE item_ordem_servico DROP CONSTRAINT fknmly75ucrbn4k8hvhk019j18r;
DROP TABLE item_peca;
DROP TABLE peca;
