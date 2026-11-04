import type { CodegenConfig } from '@graphql-codegen/cli'

const config: CodegenConfig = {
  overwrite: true,
  schema: ['./schema/**/*.graphql'],
  documents: [
    '../sudotelephony/src/main/graphql/com/sudoplatform/sudotelephony/documents/**/*.graphql',
  ],
  generates: {
    '../sudotelephony/src/main/graphql/com/sudoplatform/sudotelephony/schema.json': {
      plugins: ['introspection'],
    },
  },
  ignoreNoDocuments: true,
}

export default config
