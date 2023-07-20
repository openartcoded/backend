import { readdirSync, readFileSync, writeFileSync, unlinkSync } from 'fs';
import { join } from 'path';



const dir = './artcoded/src/main/java/tech/artcoded/websitev2/changelogs';
let files = readdirSync(dir);
for (let file of files) {
  console.log(file);
  if (file.startsWith('$')) {
    let newName = file.replace("$", "CHANGE_LOG_");

    let fileContent = readFileSync(join(dir, file), 'utf8');
    let newFileContent = fileContent.replace(file.split(".")[0], newName.split(".")[0]);
    writeFileSync(join(dir, newName), newFileContent);
    unlinkSync(join(dir, file));
  }
}
