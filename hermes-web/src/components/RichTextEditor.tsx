import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useEffect } from 'react';
import { sanitizeFragment } from '../email/sanitize';

/**
 * Rich text editor for composing email content.
 *
 * Uses TipTap/ProseMirror with a subset of the StarterKit that email clients render:
 * paragraph, h1–h3, bold, italic, underline, strike, bullet/ordered list, link, blockquote,
 * horizontal rule. Code block is disabled because email clients do not render it well.
 *
 * This schema is a quality control gate, not a security control — the security control is
 * sanitizeFragment in the pipeline that processes authored content before sending.
 */
export default function RichTextEditor(props: {
  value: string;
  onChange: (html: string) => void;
  ariaLabel: string;
  disabled?: boolean;
}): JSX.Element {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        // Disable code block since email clients do not render it well.
        codeBlock: false,
      }),
    ],
    content: props.value,
    editorProps: {
      attributes: {
        role: 'textbox',
        'aria-label': props.ariaLabel,
        'aria-multiline': 'true',
        class: 'rte-content',
      },
      /**
       * Transform pasted HTML through the same sanitizer the pipeline uses.
       * This is UX, not the security boundary — the operator sees what will be sent,
       * but the security boundary is sanitizeFragment at pipeline time, run independently.
       */
      transformPastedHTML: sanitizeFragment,
    },
    onUpdate: ({ editor }) => {
      props.onChange(editor.getHTML());
    },
  });

  // `value` is an uncontrolled seed, not a controlled value. Writing it back on every render
  // would move the caret to the end on every keystroke, because onUpdate has already pushed the
  // same HTML up to the parent. Comparing against what the editor already holds is what breaks
  // that loop: the write only happens when the change came from outside.
  useEffect(() => {
    if (editor && props.value !== editor.getHTML()) {
      editor.commands.setContent(props.value);
    }
  }, [editor, props.value]);

  // Set editor editability based on disabled prop.
  useEffect(() => {
    if (editor) {
      editor.setEditable(!props.disabled);
    }
  }, [editor, props.disabled]);

  if (!editor) {
    return <div />;
  }

  /**
   * A toolbar button that does not steal the selection.
   *
   * `onMouseDown` with preventDefault is the whole point. Without it, pressing the button blurs
   * the editor before the click handler runs, ProseMirror drops the stored mark, and the classic
   * contentEditable bug appears: with a collapsed cursor, "click Bold then type" produces plain
   * text. It looks like the button does nothing, and only for the empty-selection case — which is
   * exactly how someone uses it when starting a bold word.
   */
  function ToolbarButton(props: {
    label: string;
    active?: boolean;
    onActivate: () => void;
    children: React.ReactNode;
  }) {
    return (
      <button
        type="button"
        onMouseDown={(e) => e.preventDefault()}
        onClick={props.onActivate}
        aria-pressed={props.active ?? false}
        aria-label={props.label}
        title={props.label}
      >
        {props.children}
      </button>
    );
  }

  function handleBoldClick() {
    editor.chain().focus().toggleBold().run();
  }

  function handleItalicClick() {
    editor.chain().focus().toggleItalic().run();
  }

  function handleUnderlineClick() {
    editor.chain().focus().toggleUnderline().run();
  }

  function handleHeading1Click() {
    editor.chain().focus().toggleHeading({ level: 1 }).run();
  }

  function handleHeading2Click() {
    editor.chain().focus().toggleHeading({ level: 2 }).run();
  }

  function handleBulletListClick() {
    editor.chain().focus().toggleBulletList().run();
  }

  function handleOrderedListClick() {
    editor.chain().focus().toggleOrderedList().run();
  }

  function handleLinkClick() {
    const url = window.prompt('URL:');
    if (url === null) {
      // User cancelled the prompt.
      return;
    }
    if (url.trim().length === 0) {
      // Empty URL: unset any existing link.
      editor.chain().focus().unsetLink().run();
    } else {
      // Set the link with the provided URL.
      editor.chain().focus().setLink({ href: url }).run();
    }
  }

  function handleClearFormatClick() {
    // Both, or the button lies: clearNodes() resets the block type (heading, list, quote) and
    // leaves bold/italic/underline marks exactly where they were.
    editor.chain().focus().unsetAllMarks().clearNodes().run();
  }

  return (
    <div className="rte-shell">
      <div className="rte-toolbar">
        <ToolbarButton label="Negrito" active={editor.isActive('bold')} onActivate={handleBoldClick}>
          <strong>N</strong>
        </ToolbarButton>
        <ToolbarButton label="Itálico" active={editor.isActive('italic')} onActivate={handleItalicClick}>
          <em>I</em>
        </ToolbarButton>
        <ToolbarButton
          label="Sublinhado"
          active={editor.isActive('underline')}
          onActivate={handleUnderlineClick}
        >
          <u>U</u>
        </ToolbarButton>
        <ToolbarButton
          label="Título 1"
          active={editor.isActive('heading', { level: 1 })}
          onActivate={handleHeading1Click}
        >
          H1
        </ToolbarButton>
        <ToolbarButton
          label="Título 2"
          active={editor.isActive('heading', { level: 2 })}
          onActivate={handleHeading2Click}
        >
          H2
        </ToolbarButton>
        <ToolbarButton
          label="Lista"
          active={editor.isActive('bulletList')}
          onActivate={handleBulletListClick}
        >
          &#8226;
        </ToolbarButton>
        <ToolbarButton
          label="Lista numerada"
          active={editor.isActive('orderedList')}
          onActivate={handleOrderedListClick}
        >
          1.
        </ToolbarButton>
        <ToolbarButton label="Link" active={editor.isActive('link')} onActivate={handleLinkClick}>
          &#128279;
        </ToolbarButton>
        <ToolbarButton label="Limpar formatação" onActivate={handleClearFormatClick}>
          &#10005;
        </ToolbarButton>
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}
